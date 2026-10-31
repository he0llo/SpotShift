package he.spotshift.llo

import he.spotshift.llo.ui.PlaceSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchTest {

    @Test
    fun parsesPhotonFeaturesWithChineseNames() {
        val body = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"name":"内蒙古大厦","housenumber":"2",
               "street":"崇文门内大街","district":"东城区","city":"北京"},"geometry":{"type":"Point","coordinates":[116.4128342,39.9042826]}},
              {"type":"Feature","properties":{"name":"内蒙古大道","city":"成都市","state":"四川省"},"geometry":{"type":"Point","coordinates":[103.5126605,30.5712041]}},
              {"type":"Feature","properties":{"name":"无坐标"},"geometry":null}
            ]}
        """.trimIndent()

        val results = PlaceSearch.parsePhoton(body, "内蒙古大厦")

        assertEquals(2, results.size)
        assertEquals("内蒙古大厦", results[0].name)
        assertEquals(39.9042826, results[0].point.lat, 1e-7)
        assertEquals(116.4128342, results[0].point.lng, 1e-7)
        assertEquals("2 崇文门内大街 东城区 北京", results[0].detail)
        assertEquals("内蒙古大道", results[1].name)
        assertTrue(results[1].detail!!.contains("成都市"))
    }

    @Test
    fun usesKeywordWhenFeatureHasNoName() {
        val body = """
            {"features":[{"properties":{},"geometry":{"type":"Point","coordinates":[116.4,39.9]}}]}
        """.trimIndent()

        val results = PlaceSearch.parsePhoton(body, "测试地点")

        assertEquals("测试地点", results.single().name)
    }
}
