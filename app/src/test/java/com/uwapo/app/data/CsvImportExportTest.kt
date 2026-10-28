package com.uwapo.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CsvImportExportTest {

    private class FakeUWAPODao : UWAPODao {
        val members = mutableMapOf<String, MemberEntity>()
        val payments = mutableListOf<PaymentEntity>()

        override fun observeMembers(): Flow<List<MemberEntity>> = flowOf(members.values.toList())
        override fun observePayments(): Flow<List<PaymentEntity>> = flowOf(payments)
        override fun observeExpenses(): Flow<List<ExpenseEntity>> = flowOf(emptyList())
        override suspend fun getMember(id: String): MemberEntity? = members[id]
        override suspend fun getMemberByNumber(memberNo: String): MemberEntity? = members.values.find { it.memberNo == memberNo }
        override suspend fun getAllMembersSync(): List<MemberEntity> = members.values.toList()
        override suspend fun getPaymentByRequestId(requestId: String): PaymentEntity? = payments.find { it.requestId == requestId }
        override fun observeMemberPayments(memberId: String): Flow<List<PaymentEntity>> = flowOf(payments.filter { it.memberId == memberId })
        override suspend fun getMemberPaymentsSync(memberId: String): List<PaymentEntity> = payments.filter { it.memberId == memberId }

        override suspend fun insertMember(member: MemberEntity) { members[member.id] = member }
        override suspend fun insertMemberIgnore(member: MemberEntity): Long {
            return if (members.values.any { it.memberNo == member.memberNo }) -1L else {
                members[member.id] = member
                1L
            }
        }
        override suspend fun insertPayment(payment: PaymentEntity) { payments.add(payment) }
        override suspend fun insertPaymentIgnore(payment: PaymentEntity): Long {
            payments.add(payment)
            return 1L
        }
        override suspend fun upsertMember(member: MemberEntity) { members[member.id] = member }
        override suspend fun upsertPayment(payment: PaymentEntity) { payments.add(payment) }
        override suspend fun insertExpense(expense: ExpenseEntity) {}
        override suspend fun upsertExpense(expense: ExpenseEntity) {}
        override suspend fun updateMemberProfile(id: String, phone: String, status: String, notes: String) {}
        override suspend fun deletePaymentsForMember(memberId: String) {
            payments.removeAll { it.memberId == memberId }
        }
        override suspend fun deleteMemberOnly(id: String) {
            members.remove(id)
        }
        override suspend fun deleteMember(memberId: String) {
            deletePaymentsForMember(memberId)
            deleteMemberOnly(memberId)
        }
        override suspend fun setExpenseLocalStatus(id: String, status: String) {}
        override suspend fun setExpenseReceiptImage(id: String, imagePath: String) {}
        override suspend fun markPaymentReversed(id: String) {}
        override suspend fun deletePayment(id: String) { payments.removeAll { it.id == id } }
        override suspend fun deleteExpense(id: String) {}
        override fun observeMonthlyTotal(year: Int, month: Int): Flow<Long> = flowOf(0L)
        override fun observeYearlyTotal(year: Int): Flow<Long> = flowOf(0L)
        override fun observeActiveMemberCount(): Flow<Int> = flowOf(members.size)
        override fun observeLifetimeTotal(): Flow<Long> = flowOf(0L)
        override fun observePaidExpensesTotal(): Flow<Long> = flowOf(0L)
    }

    @Test
    fun testCsvImport() = runBlocking {
        val dao = FakeUWAPODao()
        val csv = "member_no,full_name,phone,join_date,status\r\nM101,Amina Salum,0712345678,2025-01-01,active\r\n"
        val result = CsvImportExport.import(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)), dao)
        assertEquals(1, result.membersAdded)
        assertEquals(0, result.rowsSkipped)
        assertEquals("Amina Salum", dao.getMemberByNumber("M101")?.fullName)
    }

    @Test
    fun testMemberDeletion() = runBlocking {
        val dao = FakeUWAPODao()
        val csv = "member_no,full_name,amount,year,month\r\nM101,Amina Salum,10000,2025,1\r\n"
        CsvImportExport.import(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)), dao)
        val member = dao.getMemberByNumber("M101")!!
        assertEquals(1, dao.members.size)
        assertEquals(1, dao.payments.size)

        dao.deleteMember(member.id)
        assertNull(dao.getMemberByNumber("M101"))
        assertEquals(0, dao.members.size)
        assertEquals(0, dao.payments.size)
    }

    @Test
    fun testXlsxStandardImport() = runBlocking {
        val dao = FakeUWAPODao()
        val xlsxBytes = createTestStandardXlsxBytes()
        val result = CsvImportExport.import(ByteArrayInputStream(xlsxBytes), dao)
        assertEquals(1, result.membersAdded)
        assertEquals(0, result.rowsSkipped)
        assertEquals("John Doe", dao.getMemberByNumber("M202")?.fullName)
    }

    @Test
    fun testRealMichangoFileImport() = runBlocking {
        val file = File("C:\\Users\\JEFF\\OneDrive\\Desktop\\MICHANGO 2025  HII HAPA  FROM JAN TO DEC UWAPO   .xlsx")
        if (file.exists()) {
            val dao = FakeUWAPODao()
            val result = CsvImportExport.import(file.inputStream(), dao)
            assertEquals(61, result.membersAdded)
            assertEquals(688, result.paymentsAdded)
            assertEquals(61, dao.members.size)
            assertEquals(688, dao.payments.size)
            assertEquals("MAGDALENA S.MARUA", dao.getMemberByNumber("1")?.fullName)
        }
    }

    private fun createTestStandardXlsxBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val sharedStringsXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <si><t>member_no</t></si>
                    <si><t>full_name</t></si>
                    <si><t>M202</t></si>
                    <si><t>John Doe</t></si>
                </sst>
            """.trimIndent()
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(sharedStringsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val sheetXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="s"><v>0</v></c>
                            <c r="B1" t="s"><v>1</v></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="s"><v>2</v></c>
                            <c r="B2" t="s"><v>3</v></c>
                        </row>
                    </sheetData>
                </worksheet>
            """.trimIndent()
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
