# Refactor: package by feature (backend and frontend)

Branch: `refactor/package-by-feature`. No behaviour change: files move into feature folders and only
`package` / `import` lines change. History is kept (`git log --follow` works).

Backend: `controller/ service/ repository/ model/ dto/` inside each feature folder.
Frontend: `src/features/<feature>/{pages,components,services}`, plus `app/`, `layouts/`, `shared/`, `styles/`.

Commits, in order (each ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`):
1. `refactor: move classes and files into feature folders (pure renames)`  — step `move` for backend and frontend
2. `refactor: update package and import statements for feature layout`    — step `fix` for backend and frontend
3. `docs: describe package-by-feature structure`                          — docs/architecture.md, docs/roadmap.md, this file

Two code commits on purpose: a pure-rename commit is detected by Git as 100% renames, so file history is never
lost; the edit commit comes after. Merge with a normal merge or "Rebase and merge", never "Squash and merge".

Run everything with the prompt in `REFACTOR-PROMPT.md` (or by hand: `python restructure.py move backend`,
`python restructure_frontend.py move frontend`, commit, then the same with `fix`, commit).
