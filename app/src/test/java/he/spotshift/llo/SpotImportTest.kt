package he.spotshift.llo

import he.spotshift.llo.data.CoordConverter
import he.spotshift.llo.data.CoordSystem
import he.spotshift.llo.imports.SpotImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotImportTest {

    @Test
    fun parsesAmapUriAndConvertsToWgs84() {
        val report = SpotImport.parse(
            "androidamap://viewMap?poiname=天安门&lat=39.908692&lon=116.397477&dev=0"
        )

        assertEquals(1, report.drafts.size)
        val draft = report.drafts.single()
        assertEquals("天安门", draft.name)
        assertEquals(CoordSystem.GCJ02, draft.source)
        val wgs = CoordConverter.convert(draft.rawPoint, draft.source, CoordSystem.WGS84)
        assertEquals(39.907291, wgs.lat, 0.00001)
        assertEquals(116.391236, wgs.lng, 0.00001)
    }

    @Test
    fun parsesBaiduUriAsBd09() {
        val report = SpotImport.parse(
            "baidumap://map/marker?location=39.916,116.397&title=故宫&coord_type=bd09ll"
        )

        val draft = report.drafts.single()
        assertEquals("故宫", draft.name)
        assertEquals(CoordSystem.BD09, draft.source)
        val wgs = CoordConverter.convert(draft.rawPoint, draft.source, CoordSystem.WGS84)
        assertEquals(39.908299, wgs.lat, 0.00001)
        assertEquals(116.384378, wgs.lng, 0.00001)
    }

    @Test
    fun parsesAmapWebPoiParamWithName() {
        val report = SpotImport.parse(
            "内蒙古大厦 https://wb.amap.com/?p=B000A853VZ,39.90564907779522," +
                "116.41892999410628,内蒙古大厦,崇文门内大街2号&src=app_C3300"
        )

        val draft = report.drafts.single()
        assertEquals("内蒙古大厦", draft.name)
        assertEquals(CoordSystem.GCJ02, draft.source)
        assertEquals(39.905649, draft.rawPoint.lat, 0.00001)
        assertEquals(116.418930, draft.rawPoint.lng, 0.00001)
    }

    @Test
    fun reportsUnparseableTextInsteadOfFailingSilently() {
        val report = SpotImport.parse("这条没有坐标")

        assertTrue(report.drafts.isEmpty())
        assertEquals(1, report.failures.size)
        assertTrue(report.failures.single().reason.isNotBlank())
    }
}
