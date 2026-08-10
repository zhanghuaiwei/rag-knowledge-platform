# AI 项目上下文

这里保存 AI 开发所需的轻量项目配置与本地角色上下文。

- `project.json`：可提交的项目结构、模块和验证命令配置。
- `current-role.example.md`：可提交的角色说明模板。
- `current-role.md`：当前开发者本地角色，由开发者从模板复制并填写，不应提交。
- `scaffold-manifest.json`：脚手架版本及生成文件清单。

首次使用时，将 `current-role.example.md` 复制为 `current-role.md`，只保留当前角色和阶段，并通过项目现有 `.gitignore` 忽略它。请把 `project.json` 中的 `TODO` 验证命令替换为项目真实命令。AI 开始修改前必须读取本地角色；角色发生变化时重新填写，不要在同一角色文件中混合多个阶段。
