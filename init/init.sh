#!/bin/bash
set -e

echo "Initializing Couchbase cluster..."

# Wait for the Couchbase REST API
until curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/ui/index.html | grep -q "200"; do
  echo "Waiting for Couchbase Server to start..."
  sleep 5
done

# Initialize cluster
curl -v -u Administrator:password -X POST http://127.0.0.1:8091/node/controller/setupServices \
  -d 'services=kv,index,n1ql,fts'

curl -v -X POST http://127.0.0.1:8091/settings/web \
  -d "username=admin&password=password&port=SAME"

# Configure memory quotas
curl -v -u admin:password -X POST http://127.0.0.1:8091/pools/default \
  -d 'memoryQuota=512' -d 'indexMemoryQuota=256' -d 'ftsMemoryQuota=256'

# Create bucket
curl -v -u admin:password -X POST http://127.0.0.1:8091/pools/default/buckets \
  -d 'name=faq_bucket&ramQuotaMB=256&replicaNumber=0&flushEnabled=1'

sleep 10

# Create scope and collection
curl -v -u admin:password -X POST http://127.0.0.1:8091/pools/default/buckets/faq_bucket/scopes \
  -d 'name=faq_scope'
curl -v -u admin:password -X POST http://127.0.0.1:8091/pools/default/buckets/faq_bucket/scopes/faq_scope/collections \
  -d 'name=faqs'

echo "✅ Couchbase initialized with bucket=faq_bucket, scope=faq_scope, collection=faqs"