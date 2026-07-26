@echo off
chcp 65001>nul

echo EnergyFlow repository status
echo.
git status --short --branch
echo.
echo This helper no longer stages, commits, pulls, or pushes automatically.
echo Run tests, review git diff, then use explicit Git commands when ready.

pause
