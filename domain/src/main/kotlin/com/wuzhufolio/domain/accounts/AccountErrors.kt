package com.wuzhufolio.domain.accounts

/** A1：登录失败「用户名或密码错误」——不暴露账户是否存在（PRD §2.13/interaction A1）。 */
class InvalidCredentialsException(message: String = "invalid username or password") : RuntimeException(message)

/** A2：改密原密码错误「原密码不正确」（interaction A2）。 */
class OldPasswordMismatchException(message: String = "old password is incorrect") : RuntimeException(message)

/** A3：切换账户加载失败「账户数据加载失败，请重试」（interaction A3）。 */
class AccountLoadFailedException(message: String = "account data load failed") : RuntimeException(message)

/** 切换账户密码错误「密码错误」（PRD §2.13 口径；与登录 A1 文案区分）。 */
class PasswordMismatchException(message: String = "password mismatch") : RuntimeException(message)

/** 服务层口令策略拒绝（UI 已预校验；防御重复校验入口）。 */
class WeakPasswordException(message: String = "password does not meet the minimum strength") : RuntimeException(message)

/** 用户名已存在（创建路径明确提示；登录路径不暴露存在性——A1 统一文案）。 */
class UsernameTakenException(username: String) :
    RuntimeException("username already taken: " + username)
