package com.joblens.api.config;

/**
 * 크롤러 기본 설정(사람인/잡코리아 URL)을 한 곳에서 관리하는 유틸 클래스.
 */
public final class CrawlerDefaults {

    private CrawlerDefaults() {
    }

    private static String envOrDefault(String key, String defaultValue) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key);
        }
        return v.trim();
    }

    public static final String SARAMIN_DEFAULT_URL = envOrDefault(
        "SARAMIN_LIST_URL",
        "https://www.saramin.co.kr/zf_user/search?loc_mcd=101000%2C102000%2C108000&cat_mcls=2&job_type=1&exc_keyword=php%2C%ED%97%A4%EB%93%9C%2Csi&company_cd=0%2C1%2C2%2C3%2C4%2C5%2C6%2C7%2C9%2C10&keydownAccess=&searchType=search&searchword=%ED%92%80&panel_type=&search_optional_item=y&search_done=y&panel_count=y&preview=y"
    );

    public static final String JOBKOREA_DEFAULT_URL = envOrDefault(
        "JOBKOREA_LIST_URL",
        "https://www.jobkorea.co.kr/Search?tabType=recruit&Ord=ApplyCloseDtAsc&Page_No=1&duty=1000229%2C1000230%2C1000231%2C1000232%2C1000233%2C1000234%2C1000235%2C1000236%2C1000237%2C1000239%2C1000240%2C1000238%2C1000241%2C1000242%2C1000243%2C1000244%2C1000245%2C1000246%2C1000247%2C1000417%2C1000418%2C1000419%2C1000420%2C1000421%2C1000422%2C1000423&jobtype=1&filter=3%2C1&excludeText=php%2Csi%2C%ED%97%A4%EB%93%9C"
    );
}

