#!/bin/bash
# connect 컨테이너 healthcheck. outbox 커넥터 태스크가 RUNNING일 때만 성공한다.
curl -fs http://localhost:8083/connectors/outbox/status | grep -q '"tasks":\[{"id":0,"state":"RUNNING"'
