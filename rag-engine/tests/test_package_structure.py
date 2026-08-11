"""按功能包组织和 Python 占位文件完整性约束。"""

from pathlib import Path

PACKAGE_ROOT = Path(__file__).parents[1] / "src" / "rag_engine"
FEATURE_PACKAGES = {
    "auth",
    "engine",
    "generation",
    "health",
    "indexing",
    "ingestion",
    "parsing",
    "rerank",
    "retrieval",
    "safety",
}
TECHNICAL_PACKAGES = {"api", "common", "config", "observability", "providers"}
LEGACY_LAYER_PACKAGES = {"application", "domain", "ports"}


def test_required_feature_packages_exist() -> None:
    packages = {path.parent.name for path in PACKAGE_ROOT.glob("*/__init__.py")}

    assert FEATURE_PACKAGES <= packages
    assert TECHNICAL_PACKAGES <= packages
    assert not (LEGACY_LAYER_PACKAGES & packages)


def test_no_python_file_is_empty_or_has_an_empty_package_initializer() -> None:
    python_files = sorted(PACKAGE_ROOT.rglob("*.py"))

    assert python_files
    for path in python_files:
        content = path.read_text(encoding="utf-8").strip()
        assert content, f"empty Python file: {path}"
        if path.name == "__init__.py":
            assert "__all__" in content, f"package initializer must expose its public API: {path}"
