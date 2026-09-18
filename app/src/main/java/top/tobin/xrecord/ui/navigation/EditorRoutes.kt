package top.tobin.xrecord.ui.navigation

import kotlinx.serialization.Serializable

/** 编辑已有流水时的路由参数名，与 [TransactionEditorRoute.transactionId] 保持一致。 */
const val ARG_TRANSACTION_ID = "transactionId"

@Serializable
data class TransactionEditorRoute(val transactionId: Long)
