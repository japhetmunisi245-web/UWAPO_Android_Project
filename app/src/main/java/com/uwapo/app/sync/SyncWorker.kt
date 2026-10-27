package com.uwapo.app.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uwapo.app.UWAPOApplication
import com.uwapo.app.cloud.SessionStore
import com.uwapo.app.cloud.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Base64

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = SessionStore(applicationContext)
        var token = session.accessToken()
        val clubId = session.clubId()
        val accountId = session.accountId()
        val client = SupabaseClient()
        if (!client.configured || token.isBlank() || clubId.isBlank() || accountId.isBlank()) return Result.success()

        return try {
            if (isExpiring(token) && session.refreshToken().isNotBlank()) {
                val refreshed = client.refresh(session.refreshToken())
                token = refreshed.getString("access_token")
                session.saveSession(token, refreshed.optString("refresh_token", session.refreshToken()), session.userEmail())
            }
            val dao = (applicationContext as UWAPOApplication).database.uwapoDao()
            dao.pendingMembers().forEach { member ->
                val joined = Instant.ofEpochMilli(member.joinedAt).atZone(ZoneId.of("Africa/Dar_es_Salaam")).toLocalDate()
                val body = JSONObject()
                    .put("id", member.id)
                    .put("club_id", clubId)
                    .put("member_no", member.memberNo)
                    .put("full_name", member.fullName)
                    .put("phone", member.phone.ifBlank { JSONObject.NULL })
                    .put("join_date", joined.toString())
                    .put("status", member.status)
                client.post("/rest/v1/members?on_conflict=club_id%2Cmember_no", body, token, "resolution=merge-duplicates,return=minimal")
                dao.setMemberSyncStatus(member.id, "synced")
            }

            dao.pendingPayments().forEach { payment ->
                val periodStart = LocalDate.of(payment.periodYear, payment.periodMonth, 1)
                val periodId = getOrCreatePeriod(client, token, clubId, periodStart)
                val paidAt = Instant.ofEpochMilli(payment.paidAt).toString()
                val body = JSONObject()
                    .put("p_club_id", clubId)
                    .put("p_member_id", payment.memberId)
                    .put("p_period_id", periodId)
                    .put("p_amount_tzs", payment.amountTzs)
                    .put("p_paid_at", paidAt)
                    .put("p_method", payment.method)
                    .put("p_account_id", accountId)
                    .put("p_request_id", payment.requestId)
                    .put("p_notes", "Synced from Android app")
                val response = client.rpc("record_payment", body, token)
                val rows = JSONArray(response)
                val receipt = if (rows.length() > 0) rows.getJSONObject(0).optString("receipt_no", payment.receiptNo) else payment.receiptNo
                dao.setPaymentSyncStatus(payment.id, "synced", receipt)
            }

            dao.pendingExpenses().forEach { expense ->
                val expenseDate = Instant.ofEpochMilli(expense.expenseDate)
                    .atZone(ZoneId.of("Africa/Dar_es_Salaam")).toLocalDate().toString()
                val body = JSONObject()
                    .put("p_expense_id", expense.id)
                    .put("p_club_id", clubId)
                    .put("p_account_id", accountId)
                    .put("p_category", expense.category)
                    .put("p_amount_tzs", expense.amountTzs)
                    .put("p_expense_date", expenseDate)
                    .put("p_description", expense.description)
                client.rpc("submit_expense", body, token)
                dao.setExpenseSyncStatus(expense.id, "synced")
            }
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun isExpiring(jwt: String): Boolean = try {
        val parts = jwt.split(".")
        if (parts.size < 2) true else {
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            JSONObject(payload).optLong("exp", 0L) < (System.currentTimeMillis() / 1000L + 120L)
        }
    } catch (_: Exception) { true }

    private fun getOrCreatePeriod(client: SupabaseClient, token: String, clubId: String, start: LocalDate): String {
        val startText = start.toString()
        val encodedStart = Uri.encode(startText)
        val path = "/rest/v1/contribution_periods?select=id,period_start&club_id=eq.$clubId&period_start=eq.$encodedStart&limit=1"
        val existing = client.get(path, token)
        if (existing.length() > 0) return existing.getJSONObject(0).getString("id")

        val month = YearMonth.from(start)
        val body = JSONObject()
            .put("club_id", clubId)
            .put("period_start", start.toString())
            .put("period_end", month.atEndOfMonth().toString())
            .put("due_date", month.atEndOfMonth().toString())
            .put("expected_amount_tzs", 10000)
            .put("status", "open")
        val result = client.post("/rest/v1/contribution_periods", body, token, "return=representation")
        return JSONArray(result).getJSONObject(0).getString("id")
    }
}
