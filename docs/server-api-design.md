# Server List API — Django Design

Replaces local `res/raw/bdix_servers.json` read with backend API. Supports
user-submitted servers, admin approval before publish.

## Models

```python
# servers/models.py
from django.db import models


class ISP(models.Model):
    """BDIX / local ISP network. Keyed by ASN (stable, app-resolved via
    IspDetector.kt → ipinfo.io/ip-api.com), not free-text org name — org
    string spelling varies per lookup, ASN doesn't."""
    asn = models.CharField(max_length=20, unique=True)   # "AS138296"
    name = models.CharField(max_length=120)              # display org name, e.g. "Carnival Internet"

    def __str__(self):
        return f"{self.name} ({self.asn})"


class Server(models.Model):
    """Logical server group, e.g. 'DhakaFlix FTP Servers'."""
    name = models.CharField(max_length=200)
    parser = models.CharField(
        max_length=20,
        choices=[("h5ai", "h5ai"), ("autoindex", "Autoindex"),
                  ("ftp", "FTP"), ("webdav", "WebDAV")],
        default="autoindex",
    )
    is_published = models.BooleanField(default=False)  # admin gate
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.name


class ServerMirror(models.Model):
    """One ISP-specific URL for a Server (matches urls[] array per ISP route)."""
    class Status(models.TextChoices):
        PENDING = "pending", "Pending"
        APPROVED = "approved", "Approved"
        REJECTED = "rejected", "Rejected"

    server = models.ForeignKey(Server, related_name="mirrors", on_delete=models.CASCADE)
    isp = models.ForeignKey(ISP, on_delete=models.SET_NULL, null=True, blank=True)
    url = models.URLField(max_length=500)
    requires_auth = models.BooleanField(default=False)

    status = models.CharField(max_length=10, choices=Status.choices, default=Status.PENDING)
    last_checked_online = models.BooleanField(null=True)  # client self-test result at submit time
    created_at = models.DateTimeField(auto_now_add=True)
    approved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        unique_together = ("server", "url")

    def __str__(self):
        return f"{self.server.name} · {self.url}"
```

Reasoning: existing JSON groups multiple URLs under one server name (`urls: [...]`)
— per-ISP mirrors of same content. `ServerMirror` models that 1:N, carries
approval state per mirror (not whole `Server`), since one ISP's link can be
approved while another's still pending/rejected.

## Domain

Base URL: **`https://devnerd.xyz/`** — app reads it from `BuildConfig.API_BASE_URL`
(`app/build.gradle.kts`, overridable via `local.properties` → `api.baseUrl`).

## API endpoints (DRF)

All endpoints public, no auth (no user accounts, no `submitted_by`).

```
GET  /api/servers/                 → published servers + their approved mirrors only
                                      (replaces raw/bdix_servers.json read)
GET  /api/servers/?isp_asn=AS...   → same, filtered to servers with an approved
                                      mirror for that ASN (app's Suggested-servers
                                      screen "matched" tab)
POST /api/servers/submit/          → submit new server/mirror, no auth
                                      body: { name, url, isp_asn, isp_name, requires_auth, parser? }
                                      → ISP looked up/created by isp_asn (exact match, unique)
                                      → Server looked up/created by exact name match
                                      → creates ServerMirror (status=pending) if (server, url) new
                                      → silent no-op / 200 if duplicate — no error detail leaked
```

`GET /api/servers/` response shape:

```json
[
  {
    "id": 1,
    "name": "DhakaFlix FTP Servers",
    "parser": "ftp",
    "mirrors": [
      {
        "url": "ftp://10.16.0.5/dhakaflix",
        "name": "Carnival Internet",
        "requires_auth": false
      }
    ]
  }
]
```

Mirror `name` is the ISP's display label (`isp.name`), not a nested `isp`
object or PK — app has no use for the ISP row itself, only the label next to
each mirror URL.

`isp_asn`/`isp_name` come straight from app's `IspDetector.detect()` — ASN
is the stable dedup key (org-name string varies between ipinfo/ip-api
phrasing, ASN doesn't), `isp_name` stored only as display label.

Admin panel: register `Server`, `ServerMirror` (`list_filter=status`,
`actions=[approve_mirrors]`). Admin flips `ServerMirror.status` →
`approved` (and `Server.is_published=True` once it has ≥1 approved mirror).
Bulk-approve via `ModelAdmin.actions`.

## Serializers

Public GET emits only approved mirrors:

```python
class ServerMirrorPublicSerializer(serializers.ModelSerializer):
    name = serializers.CharField(source="isp.name", default="")  # ISP display label, flat

    class Meta:
        model = ServerMirror
        fields = ["url", "name", "requires_auth"]

class ServerPublicSerializer(serializers.ModelSerializer):
    mirrors = serializers.SerializerMethodField()
    class Meta:
        model = Server
        fields = ["id", "name", "parser", "mirrors"]
    def get_mirrors(self, obj):
        qs = obj.mirrors.filter(status=ServerMirror.Status.APPROVED)
        return ServerMirrorPublicSerializer(qs, many=True).data

class ServerListView(generics.ListAPIView):
    serializer_class = ServerPublicSerializer
    permission_classes = [AllowAny]

    def get_queryset(self):
        qs = Server.objects.filter(is_published=True).distinct()
        isp_asn = self.request.query_params.get("isp_asn")
        if isp_asn:
            qs = qs.filter(mirrors__isp__asn=isp_asn, mirrors__status=ServerMirror.Status.APPROVED)
        return qs
```

Submit serializer (`AllowAny` permission):

```python
class ServerSubmitSerializer(serializers.Serializer):
    name = serializers.CharField(max_length=200)
    url = serializers.URLField(max_length=500)
    isp_asn = serializers.CharField(max_length=20)
    isp_name = serializers.CharField(max_length=120)
    requires_auth = serializers.BooleanField(default=False)
    parser = serializers.CharField(max_length=20, required=False)

    def save(self):
        d = self.validated_data
        isp, _ = ISP.objects.get_or_create(asn=d["isp_asn"], defaults={"name": d["isp_name"]})
        server, _ = Server.objects.get_or_create(
            name=d["name"], defaults={"parser": d.get("parser", "autoindex")}
        )
        ServerMirror.objects.get_or_create(
            server=server, url=d["url"], defaults={"isp": isp, "requires_auth": d["requires_auth"]}
        )
```

`get_or_create` on `(server, url)` unique_together handles dedup silently —
repeat submit of same url is a no-op, no error surfaced either way.
Rejected mirrors: status flips to `rejected` in admin, nothing sent back to
app — app just never sees it in `GET /api/servers/` (only `approved` shown).

## App-side integration

- `HttpMediaSource` / `SettingsRepository`: swap json-file read for Retrofit
  call to `GET /api/servers/`.
- "Add server" flow: after local self-test succeeds (reuse
  `ServerHealth.check`), fire `POST /api/servers/submit/`.

## Decisions

- No auth on API — public, no accounts, no `submitted_by`.
- Dedup: exact match on `Server.name` and `(server, url)`; ISP dedup by
  exact `asn` match (not name — ASN is the stable per-network key the app
  already resolves via `IspDetector`).
- No rate limit / captcha on submit endpoint.
- Rejected/pending mirrors: fully silent — app never sees them, no
  notification, no error response distinguishing duplicate vs new.
