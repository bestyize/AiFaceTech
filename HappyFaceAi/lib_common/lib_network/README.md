# lib_network

跨平台基础网络层。提供 `NetworkClient`、JSON POST 和 multipart POST 能力，Android/Desktop 使用 JVM HTTP 实现，iOS 保留平台实现入口。
业务模块通过注入 `NetworkClient` 使用网络，不直接依赖平台 API。
