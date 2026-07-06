<!--
  Sentinel Grid — Pull Request
  Fill in each section. PRs that leave the Security Checklist unaddressed will not be merged.
-->

## 🧭 Context / Description

<!-- What does this PR do and why? Link the mission/issue if applicable. -->

-

## 🏗️ Architectural Changes

<!-- New/changed modules, endpoints, services, DB schema, config, or dependencies.
     Note anything that affects the com.defense.sentinel package structure or the frontend contract. -->

-

## 🧪 Testing Logs / Proof

<!-- How was this verified? Paste command output / screenshots.
     e.g. `./mvnw clean verify`, `npm run build`, `npx ng test --watch=false`. -->

- [ ] Backend: `./mvnw clean verify` passes
- [ ] Frontend: `npm run build` + `npx ng test --watch=false` pass
- [ ] Manual verification (describe below)

```
<!-- paste relevant test / build output here -->
```

## 🔒 Security Checklist

- **JWT integrity**
  - [ ] No secrets, tokens, private keys, or `.pem` files are committed
  - [ ] `publicKey.pem`, `mp.jwt.verify.issuer`, and JWT verification config are unchanged (or changes are intentional and reviewed)
  - [ ] Protected operations still validate the bearer token / required roles
- **Endpoint exposure**
  - [ ] New/changed endpoints carry the correct auth (`@RolesAllowed`, etc.) — nothing is unintentionally public
  - [ ] CORS origins / allowed methods reviewed for any new route
  - [ ] No debug/internal endpoints or verbose error leakage introduced
- **Code styling compliance**
  - [ ] Backend passes Spotless (`./mvnw spotless:check`)
  - [ ] Frontend passes ESLint + Prettier (`npm run lint`)
  - [ ] Pre-commit hooks ran (no manual `--no-verify` bypass)
