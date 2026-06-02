# CLAUDE.md

## Project Overview

This is the Vue frontend for Medical Admin Platform. It is a medical operations back office for registration orders, order tracking, exception handling, departments, doctors, patients, platform users, visit bindings, appointment slots, and knowledge governance.

## Commands

```bash
npm run dev
npm run build
npm run preview
npm run lint
```

## Notes

- Keep business pages under the medical operations model.
- Use Chinese labels for departments, doctors, patients, visit users, slots, and registration orders.
- Do not expose raw database IDs in list pages unless explicitly needed.
- Avoid horizontal scrolling in business tables where fields can be formatted or reduced.
