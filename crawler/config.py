from __future__ import annotations

"""
크롤러 공통 설정 모듈.

사람인/잡코리아 기본 검색 URL과 도메인(origin)을 한 곳에서 관리한다.
환경 변수로 덮어쓸 수 있도록 해두었기 때문에,
배포 환경에서 주소를 변경하고 싶을 때 코드 수정 없이도 조정 가능하다.
"""

import os


def _env_or_default(key: str, default: str) -> str:
    value = os.getenv(key)
    if value is None or not value.strip():
        return default
    return value.strip()


# 사람인 ---------------------------------------------------------------------

# 사람인 도메인(origin)
SARAMIN_BASE_ORIGIN: str = _env_or_default(
    "SARAMIN_BASE_ORIGIN",
    "https://www.saramin.co.kr",
)

# 사람인 기본 검색 URL (필터 포함)
DEFAULT_SARAMIN_LIST_URL: str = _env_or_default(
    "SARAMIN_LIST_URL",
    "https://www.saramin.co.kr/zf_user/search?loc_mcd=101000%2C102000%2C108000&cat_mcls=2&job_type=1&exc_keyword=php%2C%ED%97%A4%EB%93%9C%2Csi&company_cd=0%2C1%2C2%2C3%2C4%2C5%2C6%2C7%2C9%2C10&keydownAccess=&searchType=search&searchword=%ED%92%80&panel_type=&search_optional_item=y&search_done=y&panel_count=y&preview=y"
)


# 잡코리아 -------------------------------------------------------------------

# 잡코리아 도메인(origin)
JOBKOREA_BASE_ORIGIN: str = _env_or_default(
    "JOBKOREA_BASE_ORIGIN",
    "https://www.jobkorea.co.kr",
)

# 잡코리아 기본 검색 URL (필터 포함)
DEFAULT_JOBKOREA_LIST_URL: str = _env_or_default(
    "JOBKOREA_LIST_URL",
    "https://www.jobkorea.co.kr/Search?tabType=recruit&Ord=ApplyCloseDtAsc&Page_No=1&duty=1000229%2C1000230%2C1000231%2C1000232%2C1000233%2C1000234%2C1000235%2C1000236%2C1000237%2C1000239%2C1000240%2C1000238%2C1000241%2C1000242%2C1000243%2C1000244%2C1000245%2C1000246%2C1000247%2C1000417%2C1000418%2C1000419%2C1000420%2C1000421%2C1000422%2C1000423&jobtype=1&filter=3%2C1&excludeText=php%2Csi%2C%ED%97%A4%EB%93%9C",
)

