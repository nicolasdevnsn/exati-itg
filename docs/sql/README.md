# SQL scripts — SIP database (`ami` schema)

DDL for the tables `exati-itg` owns inside the vendor's `ami` schema. The app
**never executes DDL**; these are run by hand.

Naming convention for app-owned tables there: `exati_itg_<entity>` —
snake_case (hyphens would need backticks in MySQL), singular, matching the
surrounding `ami` schema style. The prefix marks them as ours, not Sanxing's.

| Script | Purpose |
|---|---|
| `exati_itg_ticket.sql` | Creates `exati_itg_ticket`, the mirror of tickets submitted to the Exati IoT Hub (see INTEGRATION.md §4b). Safe to re-run. |
| `exati_itg_ticket_add_recheck_fields.sql` | Adds `reported_at`, `closed_at`, `closing_reason` — the fields the recheck job syncs. Only needed for tables created before 2026-09-02. |

## Running them

The `mysql` client lives on the SSH VM, not on a dev laptop — pipe the script
through `ssh`. A copy of both scripts (with the password filled in) sits next
to the key in `Projects/EXATI/`:

```bash
ssh -i ../EXATI/hong_baiyi.pem hong_baiyi@3.88.22.232 \
    "mysql -h 34.232.210.135 -u ami -p'<password>' ami" \
    < exati_itg_ticket.sql
```

Success is silent. To verify:

```bash
ssh -i ../EXATI/hong_baiyi.pem hong_baiyi@3.88.22.232 \
    "mysql -h 34.232.210.135 -u ami -p'<password>' ami -e 'SHOW CREATE TABLE exati_itg_ticket\G'"
```

Credentials are the platform's own (`~/mdc/application-brazil_light.properties`
on the VM). Keep them out of the repo copies.
