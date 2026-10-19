# Elasticsearch + nori(한국어 형태소 분석) 플러그인. 공식 이미지에 nori가 없어서 만든다(R10).
# 기반 태그는 ADR 0009 이미지 표와 같게 둔다. 플러그인은 ES와 같은 버전을 받는다.
ARG ES_VERSION=9.4.6
FROM elasticsearch:${ES_VERSION}

RUN bin/elasticsearch-plugin install --batch analysis-nori
