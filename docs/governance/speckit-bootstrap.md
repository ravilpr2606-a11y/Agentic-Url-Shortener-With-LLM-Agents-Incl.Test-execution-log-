# SpecKit Bootstrap

Execute these commands in the actual assessment repository before final submission, using the version recorded in `speckit-version.txt`:

```bash
uv tool install specify-cli
specify version
specify --help
specify init . --integration claude
find .claude -maxdepth 3 -type f | sort
find .specify -maxdepth 3 -type f | sort
git status
```

Then follow the lifecycle in the assessment guide: constitution -> specify -> clarify -> human requirements approval -> plan -> ADR approval -> checklist -> tasks -> analyze -> pre-implementation review -> implement -> TDD/checkpoints -> scenarios -> converge -> independent assessment -> release readiness.

Do not claim that these commands were executed unless they were actually executed. Do not invent a SpecKit version.
