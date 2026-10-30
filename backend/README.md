# KitchenOS service modules

Backend source for account/device flows, recipe identity and validation, pantry
handling and optional AI integrations. This is a developer snapshot; production
deployment configuration and cloud data are excluded.

```sh
npm ci
npm run build
npm test
```

Tests use test doubles and synthetic identities. The included authored recipe
examples document software behavior and are not physically validated cooking
instructions. `npm start` requires your own Firebase, HTTPS origin, enabled auth
providers and Vertex configuration; see [the build guide](../docs/BUILDING.md).
