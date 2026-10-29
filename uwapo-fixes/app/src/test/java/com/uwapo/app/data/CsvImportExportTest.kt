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
        val batches = mutableMapOf<Int, BatchEntity>()
        override fun observeBatches(): Flow<List<BatchEntity>> = flowOf(batches.values.sortedByDescending { it.year })
        override suspend fun getAllBatchesSync(): List<BatchEntity> = batches.values.sortedByDescending { it.year }
        override suspend fun insertBatchIgnore(batch: BatchEntity): Long {
            return if (batches.containsKey(batch.year)) -1L else { batches[batch.year] = batch; 1L }
        }
        override suspend fun getMembersForBatchSync(batchYear: Int): List<MemberEntity> = members.values.filter { it.batchYear == batchYear }
        override suspend fun updateMemberDetails(id: String, memberNo: String, fullName: String, phone: String, status: String, notes: String) {
            members[id]?.let { members[id] = it.copy(memberNo = memberNo, fullName = fullName, phone = phone, status = status, notes = notes) }
        }
        override suspend fun deletePaymentsForBatch(batchYear: Int) {
            val ids = members.values.filter { it.batchYear == batchYear }.map { it.id }.toSet()
            payments.removeAll { it.memberId in ids }
        }
        override suspend fun deleteMembersForBatch(batchYear: Int) {
            members.values.removeAll { it.batchYear == batchYear }
        }
        override suspend fun deleteBatchRow(batchYear: Int) { batches.remove(batchYear) }
        override suspend fun deleteBatch(batchYear: Int) {
            deletePaymentsForBatch(batchYear)
            deleteMembersForBatch(batchYear)
            deleteBatchRow(batchYear)
        }
        override suspend fun getMember(id: String): MemberEntity? = members[id]
        override suspend fun getMemberByNumber(batchYear: Int, memberNo: String): MemberEntity? = members.values.find { it.batchYear == batchYear && it.memberNo == memberNo }
        override suspend fun getAllMembersSync(): List<MemberEntity> = members.values.toList()
        override suspend fun getPaymentByRequestId(requestId: String): PaymentEntity? = payments.find { it.requestId == requestId }
        override fun observeMemberPayments(memberId: String): Flow<List<PaymentEntity>> = flowOf(payments.filter { it.memberId == memberId })
        override suspend fun getMemberPaymentsSync(memberId: String): List<PaymentEntity> = payments.filter { it.memberId == memberId }

        override suspend fun insertMember(member: MemberEntity) { members[member.id] = member }
        override suspend fun insertMemberIgnore(member: MemberEntity): Long {
            return if (members.values.any { it.batchYear == member.batchYear && it.memberNo == member.memberNo }) -1L else {
                members[member.id] = member
                1L
            }
        }
        override suspend fun insertPayment(payment: PaymentEntity) { payments.add(payment) }
        override suspend fun deleteImportedPaymentsForYear(year: Int) {
            payments.removeAll { it.periodYear == year && it.receiptNo.startsWith("IMPORT-") }
        }
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
        override fun observeActiveMemberCount(batchYear: Int): Flow<Int> = flowOf(members.values.count { it.batchYear == batchYear })
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
        assertEquals("Amina Salum", dao.getMemberByNumber(java.time.LocalDate.now().year, "M101")?.fullName)
    }

    @Test
    fun testMemberDeletion() = runBlocking {
        val dao = FakeUWAPODao()
        val csv = "member_no,full_name,amount,year,month\r\nM101,Amina Salum,10000,2025,1\r\n"
        CsvImportExport.import(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)), dao)
        val member = dao.getMemberByNumber(2025, "M101")!!
        assertEquals(1, dao.members.size)
        assertEquals(1, dao.payments.size)

        dao.deleteMember(member.id)
        assertNull(dao.getMemberByNumber(2025, "M101"))
        assertEquals(0, dao.members.size)
        assertEquals(0, dao.payments.size)
    }

    @Test
    fun testBatchesAreIndependent() = runBlocking {
        val dao = FakeUWAPODao()
        val csv = "member_no,full_name,amount,year,month\r\nM101,Amina Salum,10000,2025,1\r\n"
        val csv2 = "member_no,full_name,amount,year,month\r\nM101,Amina Salum,20000,2026,2\r\n"
        // Same person imported into two different batches
        CsvImportExport.import(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)), dao, targetStartYear = 2025)
        CsvImportExport.import(ByteArrayInputStream(csv2.toByteArray(Charsets.UTF_8)), dao, targetStartYear = 2026)

        assertEquals(2, dao.members.size)
        val m2025 = dao.getMemberByNumber(2025, "M101")!!
        val m2026 = dao.getMemberByNumber(2026, "M101")!!
        assert(m2025.id != m2026.id)
        assertEquals(listOf(2025), dao.payments.filter { it.memberId == m2025.id }.map { it.periodYear })
        assertEquals(listOf(2026), dao.payments.filter { it.memberId == m2026.id }.map { it.periodYear })
        assertEquals(setOf(2025, 2026), dao.batches.keys)

        // A batch import ignores rows that belong to another year
        val mixed = "member_no,full_name,amount,year,month\r\nM102,Juma Ali,10000,2024,3\r\n"
        CsvImportExport.import(ByteArrayInputStream(mixed.toByteArray(Charsets.UTF_8)), dao, targetStartYear = 2026)
        val m102 = dao.getMemberByNumber(2026, "M102")!!
        assertEquals(0, dao.payments.count { it.memberId == m102.id })
        assertEquals(0, dao.payments.count { it.periodYear == 2024 })
    }

    @Test
    fun testDeleteBatchLeavesOtherBatchesAlone() = runBlocking {
        val dao = FakeUWAPODao()
        val a = "member_no,full_name,amount,year,month\r\nM101,Amina Salum,10000,2025,1\r\n"
        val b = "member_no,full_name,amount,year,month\r\nM101,Amina Salum,20000,2026,2\r\n"
        CsvImportExport.import(ByteArrayInputStream(a.toByteArray(Charsets.UTF_8)), dao, targetStartYear = 2025)
        CsvImportExport.import(ByteArrayInputStream(b.toByteArray(Charsets.UTF_8)), dao, targetStartYear = 2026)

        dao.deleteBatch(2025)

        assertNull(dao.getMemberByNumber(2025, "M101"))
        assertEquals("Amina Salum", dao.getMemberByNumber(2026, "M101")?.fullName)
        assertEquals(1, dao.payments.size)
        assertEquals(2026, dao.payments.single().periodYear)
        assertEquals(setOf(2026), dao.batches.keys)
    }

    @Test
    fun testXlsxStandardImport() = runBlocking {
        val dao = FakeUWAPODao()
        val xlsxBytes = createTestStandardXlsxBytes()
        val result = CsvImportExport.import(ByteArrayInputStream(xlsxBytes), dao)
        assertEquals(1, result.membersAdded)
        assertEquals(0, result.rowsSkipped)
        assertEquals("John Doe", dao.getMemberByNumber(java.time.LocalDate.now().year, "M202")?.fullName)
    }

    @Test
    fun testRealMichangoFileImport() = runBlocking {
        val file = File("C:\\Users\\JEFF\\OneDrive\\Desktop\\MICHANGO 2025  HII HAPA  FROM JAN TO DEC UWAPO   .xlsx")
        if (file.exists()) {
            val dao = FakeUWAPODao()
            val result = CsvImportExport.import(file.inputStream(), dao)
            assertEquals(57, result.membersAdded)
            assertEquals(57, dao.members.size)
            assertEquals("MAGDALENA S.MARUA", dao.members.values.first { it.memberNo == "1" }.fullName)
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
