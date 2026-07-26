---
name: energyflow-security
description: Security checklist — API key management, input validation, data privacy, sensitive information leakage
---

# EnergyFlow — Security Checklist

**Use when**: Involving API keys / network requests / user data / external input.

## API Key Management

### DeepSeek API Key
- ✅ Stored in DataStore `energy_flow_preferences`
- ✅ User manually enters in settings page, default empty
- ✅ When not configured, `analyze()` returns null, silent degradation
- ⚠️ Do not hardcode API keys in code
- ⚠️ Do not print API keys in logs

### Weather API
- ✅ Open-Meteo is free, no key needed
- ✅ No special security handling needed

### Checklist
- [ ] Does the new external API need a key?
- [ ] Key stored in DataStore (not hardcoded, not in SharedPreferences plaintext)
- [ ] Key is optional (graceful degradation when not configured, no crash)
- [ ] Error responses do not leak keys (no URL+key in exception messages)

## Input Validation

### SmartInputParser Security Checks
- ✅ Regex first: most input does not trigger network
- ✅ AI fallback: only calls DeepSeek API after parsing fails
- ⚠️ User input sent directly to AI, no injection detection

### Potential Injection Risks
```kotlin
// SmartInputParser sends user input directly to DeepSeek
// This is fine (AI API is not SQL database)
// But note: users may write sensitive information in notes
```

### UI Input Security Checks
- [ ] Numeric input validation (non-negative, reasonable range)
- [ ] Text input length limit (prevent OOM from very long strings)
- [ ] Date input validation (valid date range)

## Data Privacy

### Local Storage
- ✅ Energy consumption data stored in local Room database (no internet)
- ✅ No cloud sync, no analytics platform reporting
- ⚠️ Export function writes data to file, user manages it themselves

### Network Transmission
- ✅ Weather query: only sends coordinates and time range, no personal information
- ✅ Theme distribution: only GET requests, no user data sent
- ⚠️ AI analysis: sends energy data and notes to DeepSeek API (third party)
  - Notes may contain personal information
  - User aware of risk (explicit when configuring key in settings)

### Checklist
- [ ] Does the new feature collect user data?
- [ ] Where is the collected data stored? (local vs cloud)
- [ ] Is data transmission encrypted? (HTTPS — Ktor default)
- [ ] Can users delete data? (Clear database already supported)

## WebView / External Content
- ❌ Currently no WebView / no third-party content loading
- If added in future: check JavaScript injection, URL whitelist

## Permissions
| Permission | Purpose | Risk |
|-----------|---------|------|
| INTERNET | Weather/AI/Theme API | Low |
| CAMERA | OCR meter reading | Medium — user controlled |
| POST_NOTIFICATIONS | Desktop widget updates | Low |

## Security Commit Checklist
```bash
# Check for hardcoded API keys / tokens
grep -rn "sk-[a-zA-Z0-9]\{20,\}" app/src/ shared/src/ --include="*.kt"
grep -rn "api[_-]?key" app/src/ shared/src/ --include="*.kt" -i

# Check for hardcoded passwords
grep -rn "password\s*=" app/src/ shared/src/ --include="*.kt" -i
```

## Related Skills
- Pre-scan: `energyflow-quick-scan` — scan + security check before committing
- Commit: `energyflow-commit` — use fix type for security-related fixes
