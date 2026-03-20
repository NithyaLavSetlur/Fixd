# Fixd Zero-Cost Backend

This worker hosts the wake-up validation endpoint on Cloudflare Workers.

## Secret handling

Do not commit real secrets to GitHub.

Use one of these local-only options:

1. Create a local file named `.dev.vars` in this folder for local development.
2. Use `wrangler secret put GEMINI_API_KEY` for deployed secrets.

You can start from:

- `.dev.vars.example`

Example local file:

```bash
GEMINI_API_KEY=your_real_key_here
FIREBASE_PROJECT_ID=fixd-4ae81
```

The real `.dev.vars` file is gitignored.

## Local install

```bash
npm install
```

## Set secret

```bash
npx wrangler secret put GEMINI_API_KEY
```

For local development, create:

```bash
backend/worker/.dev.vars
```

and put your real key there instead of committing it.

## Deploy

```bash
npx wrangler deploy
```

The deployed worker URL should be pasted into:

- `app/src/main/res/values/strings.xml`
  - `wake_validation_endpoint`
