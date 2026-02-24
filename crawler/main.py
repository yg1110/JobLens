# -*- coding: utf-8 -*-
"""
CLI 엔트리포인트(실행 스크립트)

기능:
- 사람인 검색 URL(필터 포함)을 기준으로
  1) 목록(list) 페이지에서 공고들을 수집하고
  2) (옵션) 각 공고의 상세(view-ajax → iframe view-detail)까지 수집한 뒤
  3) JSON 파일로 저장한다.

사용 예:
- 목록 3페이지 수집:
  python main.py --pages 3

- 목록 2페이지 + 상세 수집(상위 20건만):
  python main.py --pages 2 --detail --detail-limit 20

- 상세 수집 + OCR 활성화:
  python main.py --detail --ocr

- 상세 HTML 원문까지 저장(파일 커짐 주의):
  python main.py --detail --save-detail-html
"""
import argparse
import sys

from saramin.crawler import crawl_list, enrich_jobs_with_details, save_json


# 기본 검색 URL:
# - 사람인 검색 결과 페이지에 각종 필터(cat_kewd, company_cd, recruitSort 등)가 포함되어 있음
# - recruitPage=1로 시작하지만, 실제 페이지 이동은 list_urls.build_paged_url에서 recruitPage를 덮어씀
DEFAULT_URL = (
  "https://www.saramin.co.kr/zf_user/jobs/list/job-category?page=1&cat_kewd=87%2C84%2C92&job_type=1&exc_keyword=php&loc_mcd=101000%2C102000%2C108000&search_optional_item=y&search_done=y&panel_count=y&preview=y&sort=RD&isAjaxRequest=1&page_count=50&type=job-category&is_param=1&isSearchResultEmpty=1&isSectionHome=0&searchParamCount=4#searchTitle"
)


def parse_args(argv):
    """
    CLI 인자를 파싱한다.

    argv:
    - 일반적으로 sys.argv[1:]를 전달
    - 테스트/프로그램 내부 호출 시 임의 리스트를 넣을 수 있음

    리턴:
    - argparse.Namespace: args.url, args.pages, args.detail ... 형태로 접근 가능
    """
    ap = argparse.ArgumentParser(
        prog="saramin-crawler",
        description="사람인 목록 + (옵션) 상세(view-ajax→view-detail) 수집 후 JSON 저장",
    )

    # -------------------------
    # 목록(list) 수집 관련 옵션
    # -------------------------
    ap.add_argument("--url", default=DEFAULT_URL, help="사람인 검색 URL(필터 포함)")
    ap.add_argument("--pages", type=int, default=1, help="목록 크롤링 페이지 수")
    ap.add_argument("--list-delay", type=float, default=1.8, help="목록 요청 간 딜레이(초)")

    # -------------------------
    # 상세(detail) 수집 관련 옵션
    # -------------------------
    # --detail 플래그가 켜져 있을 때만 상세 수집(enrich) 수행
    ap.add_argument("--detail", action="store_true", help="상세(view-ajax→view-detail)까지 수집")

    # 상세 수집 개수 제한:
    # - None: 전체 공고에 대해 상세 수집
    # - 숫자: 상위 N건만 상세 수집 (나머지는 list 정보만 가진 상태로 저장)
    ap.add_argument("--detail-limit", type=int, default=None, help="상세 수집 개수 제한 (예: 20). 미지정 시 전체")

    # 상세 요청 간 딜레이(초):
    # - view-ajax + iframe_html 요청이 포함되므로 list보다 조금 짧거나 유사하게 설정 가능
    ap.add_argument("--detail-delay", type=float, default=1.2, help="상세 요청 간 딜레이(초)")

    # 상세 원문 HTML 저장 여부:
    # - 디버깅/재파싱에는 유용하지만 JSON 크기가 크게 증가
    ap.add_argument("--save-detail-html", action="store_true", help="상세 HTML을 JSON에 포함(파일 커짐)")

    # -------------------------
    # OCR 관련 옵션
    # -------------------------
    # OCR은 이미지 기반 상세(텍스트 거의 없음)에서만 fallback로 쓰도록 설계됨
    ap.add_argument("--ocr", action="store_true", help="이미지 기반 상세 OCR 활성화")
    ap.add_argument("--ocr-lang", default="kor+eng", help="OCR 언어(기본: kor+eng)")
    ap.add_argument("--ocr-max-images", type=int, default=5, help="OCR할 이미지 상한(기본 5)")

    # -------------------------
    # 기타 옵션
    # -------------------------
    ap.add_argument("--debug", action="store_true", help="디버그 출력")
    ap.add_argument("--out", default="saramin_jobs.json", help="저장 파일명")

    return ap.parse_args(argv)


def main(argv=None):
    """
    CLI 실행의 메인 함수.

    흐름:
    1) 인자 파싱
    2) crawl_list로 목록 수집
    3) --detail이면 enrich_jobs_with_details로 상세 수집
    4) save_json으로 결과 저장
    """
    # argv가 None이면 실제 커맨드라인 인자(sys.argv[1:]) 사용
    args = parse_args(argv or sys.argv[1:])

    # 1) 목록 수집
    jobs = crawl_list(base_url=args.url, pages=args.pages, delay=args.list_delay)

    # 디버그 출력(개수, 첫 번째 URL 등)
    if args.debug:
        print("list_count:", len(jobs))
        print("first_url:", jobs[0].url if jobs else None)

    # 2) 상세 수집(옵션)
    if args.detail:
        jobs = enrich_jobs_with_details(
            jobs,
            # 상세 요청 시 Referer로 목록 URL을 넘겨주는 목적(차단/검증 대응)
            list_referer=args.url,
            delay=args.detail_delay,
            limit=args.detail_limit,
            debug=args.debug,
            ocr=args.ocr,
            ocr_max_images=args.ocr_max_images,
        )

    # 3) JSON 저장
    save_json(args.out, jobs)

    # 요약 출력
    print(f"OK: {len(jobs)}건 저장 → {args.out}")


if __name__ == "__main__":
    main()
