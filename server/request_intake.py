"""Public loan enquiries: record for review, never grant account access."""
import hashlib
import json
import re
import uuid
from datetime import date
from decimal import Decimal, InvalidOperation


def mobile_number(value):
    digits = re.sub(r"\D", "", str(value or ""))
    if len(digits) == 12 and digits.startswith("91"):
        digits = digits[2:]
    elif len(digits) == 11 and digits.startswith("0"):
        digits = digits[1:]
    return digits if re.fullmatch(r"[6-9][0-9]{9}", digits) else None


def text(value, limit):
    if not isinstance(value, str) or len(value) > limit:
        raise ValueError("Text field is invalid or too long")
    return value.strip()


def accept_request(state, body):
    mobile = mobile_number(body.get("mobile"))
    aadhaar = text(body.get("aadhaarLast4", ""), 4)
    name = text(body.get("name", ""), 120)
    if not mobile or not re.fullmatch(r"[0-9]{4}", aadhaar) or not name:
        raise ValueError("Naam, valid 10-digit mobile aur Aadhaar ke last 4 digits required hain")
    try:
        amount = Decimal(str(body.get("amount")))
        tenure = Decimal(str(body.get("tenure")))
    except InvalidOperation:
        raise ValueError("Amount ya EMI count invalid hai")
    if not amount.is_finite() or not 0 < amount <= 100000000 or amount != amount.quantize(Decimal("0.01")):
        raise ValueError("Valid positive amount, maximum 2 decimal places, required hai")
    if not tenure.is_finite() or tenure != tenure.to_integral_value() or not 1 <= tenure <= 10000:
        raise ValueError("EMI count positive whole number hona chahiye")
    frequency = body.get("frequency")
    if frequency not in ("Daily", "Monthly", "Yearly"):
        raise ValueError("EMI frequency invalid hai")
    request_id = text(body.get("requestId", ""), 80)
    if not re.fullmatch(r"[A-Za-z0-9_-]{16,80}", request_id):
        raise ValueError("Request ID missing; page refresh karke dobara submit karein")
    payload = dict(name=name, mobile=mobile, aadhaarLast4=aadhaar,
                   amount=str(amount), tenure=int(tenure), frequency=frequency,
                   purpose=text(body.get("purpose", ""), 500), note=text(body.get("note", ""), 1000))
    fingerprint = hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()
    rows = state.setdefault("loanRequests", [])
    existing = next((r for r in rows if r.get("requestId") == request_id), None)
    if existing:
        if existing.get("fingerprint") != fingerprint:
            raise ValueError("Request details changed; start a new request")
        return {"ok": True, "receipt": existing["id"]}
    matches = [c for c in state.get("customers", [])
               if mobile_number(c.get("mobile")) == mobile
               and re.sub(r"\D", "", str(c.get("aadhaar", "")))[-4:] == aadhaar]
    customer = matches[0] if len(matches) == 1 else None
    receipt = "LR" + uuid.uuid4().hex
    rows.append({**payload, "amount": float(amount), "id": receipt,
                 "requestId": request_id, "fingerprint": fingerprint,
                 "customerId": customer["id"] if customer else None,
                 "verification": "Matched" if customer else "Needs review",
                 "status": "Pending", "date": date.today().isoformat(),
                 "source": "External Customer Form"})
    # Never disclose whether a number is registered or return customer records.
    return {"ok": True, "receipt": receipt}
