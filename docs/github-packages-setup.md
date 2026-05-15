# GitHub Packages — One-Time Local Setup

This doc explains how to configure your local Maven so `mvn deploy`
can publish to GitHub Packages, and so `mvn install` can consume
packages from it.

You only need to do this once per machine.

---

## Step 1 — Create a Personal Access Token (PAT)

1. Go to **GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)**
2. Click **Generate new token (classic)**
3. Give it a name: `examdc-packages`
4. Select scopes:
   - ✅ `write:packages`
   - ✅ `read:packages`
   - ✅ `delete:packages` (optional, for cleanup)
5. Click **Generate token**
6. **Copy the token now** — GitHub won't show it again

---

## Step 2 — Add Credentials to ~/.m2/settings.xml

Maven reads credentials from `~/.m2/settings.xml` (never from `pom.xml`,
so secrets never end up in version control).

Create or edit `~/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <!--
        The id here MUST match:
          - <id>github</id> in pom.xml <distributionManagement>
          - server-id: github in the GitHub Actions workflow
      -->
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_PERSONAL_ACCESS_TOKEN</password>
    </server>
  </servers>
</settings>
```

Replace `YOUR_GITHUB_USERNAME` and `YOUR_PERSONAL_ACCESS_TOKEN` with real values.

---

## Step 3 — Publish Locally (test it works)

```bash
cd examdc/
mvn deploy -DskipTests
```

You should see output ending in:
```
[INFO] BUILD SUCCESS
[INFO] Published: https://maven.pkg.github.com/YOUR_USERNAME/examdc/...
```

Then check: **GitHub → your repo → Packages** — `com.examd:examdc` should appear.

---

## Step 4 — Consuming the Package (optional)

If another project wants to depend on ExamdC:

```xml
<!-- In their pom.xml -->
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/YOUR_USERNAME/examdc</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.examd</groupId>
    <artifactId>examdc</artifactId>
    <version>0.1.0</version>
  </dependency>
</dependencies>
```

They also need the same `~/.m2/settings.xml` entry (GitHub Packages
requires authentication even for public packages — this is a GitHub
design decision, not a Maven one).

---

## In GitHub Actions — No Setup Needed

The CI/release workflows use `GITHUB_TOKEN` (automatically injected by
GitHub Actions) and `setup-java` with `server-id: github`. No PAT needed
in Actions — it all works with the built-in token.

---

## Troubleshooting

| Error | Fix |
|-------|-----|
| `401 Unauthorized` | Check PAT has `write:packages`, check `settings.xml` server id matches pom.xml |
| `409 Conflict` | Version already published. Bump version in pom.xml. |
| `Could not find artifact` | PAT needs `read:packages` to consume; check settings.xml |
