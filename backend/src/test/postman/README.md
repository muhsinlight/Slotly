# Calendar API — Postman

Import `calendar.postman_collection.json` into Postman, or run it with Newman:

```powershell
npx --yes newman@6.2.1 run backend/src/test/postman/calendar.postman_collection.json --bail
```

Run from the repository root against a running **disposable test API** at `http://127.0.0.1:8081` backed by the browser-test database. Never use a production or development database: the collection creates synthetic users, a place, a service and an appointment. To change the local test API address, pass `--env-var baseUrl=http://127.0.0.1:8081/api` or edit `baseUrl` in Postman.

The collection is Postman v2.1 format. It covers 25 requests and 30 assertions: closure, availability, special hours, booking conflicts, rollback, removing exceptions, weekly-hour protection, invalid input, anonymous access, customer role and another owner's access. It uses a new username suffix on each fresh run and stores tokens only in collection runtime variables. No account credentials or tokens from a real environment are needed.
