"""MinIO/S3 对象存储 provider（读取原始文档）。"""

from __future__ import annotations

from minio import Minio
from minio.error import S3Error

from rag_engine.providers.ports import ObjectStore


class MinioObjectStore(ObjectStore):
    """按 S3 兼容 API 读取不可变对象。

    object_key 即 S3 对象名（形如 ``1/2026/08/<uploadId>-<file>``），
    Java 侧 ObjectStorePort 写入的 key 规则与之一致，Python 直接按同名读取。
    """

    def __init__(self, *, endpoint: str, access_key: str, secret_key: str, bucket: str) -> None:
        # endpoint 形如 http://host:9000；minio SDK 需要 host:port 形式（自动补 http 前缀）。
        self._client = Minio(endpoint, access_key=access_key, secret_key=secret_key, secure=False)
        self._bucket = bucket

    def get(self, object_key: str) -> bytes:
        """读取对象字节；不存在或访问失败时抛异常（由摄取流水线 fail-closed）。"""
        response = self._client.get_object(self._bucket, object_key)
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()

    def head(self, object_key: str) -> bool:
        """判断对象是否存在（不返回正文）。"""
        try:
            self._client.stat_object(self._bucket, object_key)
            return True
        except S3Error as exc:
            if exc.code == "NoSuchKey":
                return False
            raise
