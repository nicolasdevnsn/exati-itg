# API client collections

## `exati-itg.postman_collection.json`

Ready-to-use Postman collection covering every route in the API.

### How to load it

1. Open Postman → **File → Import** (or `Ctrl+O`).
2. Drop `exati-itg.postman_collection.json` into the dialog.
3. The collection appears in the sidebar as **Exati ITG API** with four folders:
   - **Auth** — `Register`, `Login` (open)
   - **Protected** — `Ping` (Bearer JWT)
   - **Ops** — `Health`, `Info`, `Prometheus`, `Metrics`
   - **Docs** — `OpenAPI 3 JSON`, `Swagger UI`

### How it handles the token

The collection has these variables:

| Variable      | Default                  | Set by             |
|---------------|--------------------------|--------------------|
| `baseUrl`     | `http://localhost:8080`  | manual             |
| `username`    | `alice`                  | manual             |
| `password`    | `password-strong-1`      | manual             |
| `accessToken` | (empty)                  | **auto** by the test script on `Register` / `Login` |

Run **Register** once (or **Login** if the user already exists). The test script extracts `accessToken` from the JSON body and stores it as a collection variable. Every protected request uses `Authorization: Bearer {{accessToken}}` via the collection-level auth setting — no manual copy-paste.

### Typical first-run sequence

1. `gradlew bootRun` — start the API on `localhost:8080`.
2. Postman → **Auth → Register** → 201 (token saved automatically).
3. Postman → **Protected → Ping** → 200 with `{"message":"pong",...}`.

### Tokens expire after 60 min

When `/api/v1/ping` starts returning 401, just re-run **Auth → Login** and the variable refreshes.

### Other clients

The collection JSON is OpenAPI-adjacent but not OpenAPI itself — if you want to use **Insomnia**, **Bruno**, or **Hoppscotch**, import the API's own OpenAPI document instead:

```
GET http://localhost:8080/v3/api-docs
```

That document is auto-generated from the controllers + springdoc annotations and is always in sync with the running code.
