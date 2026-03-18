"""
Fraud Risk Scoring Engine

Assigns fraud risk scores (0-100) to financial transactions based on
configurable rules and generates a transaction-level risk report.

Rules:
  - Transactions above 10,000 are high risk.
  - CASH_OUT and TRANSFER are higher risk transaction types.
  - Transactions to new or previously unseen destination accounts are risky.
  - Rapid sequence of transactions from the same account increases risk.
  - Fraudulent transactions often involve high amounts followed by cash-out.
  - Risk levels: LOW (<40), MEDIUM (40-70), HIGH (>70).
"""

import csv
import os
from collections import defaultdict


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
HIGH_AMOUNT_THRESHOLD = 10_000
HIGH_RISK_TYPES = {"CASH_OUT", "TRANSFER"}

# Weight each risk factor contributes to the final score (must sum to ~100 max)
WEIGHT_HIGH_AMOUNT_FLAG = 30  # binary: amount > threshold (dominant signal)
WEIGHT_AMOUNT_SCALE = 10      # graduated scale for very large amounts
WEIGHT_TYPE = 20               # risky transaction type
WEIGHT_NEW_DEST = 15           # unseen destination account
WEIGHT_RAPID_TXN = 15          # rapid transactions from same origin
WEIGHT_HIGH_THEN_CASHOUT = 10  # high amount followed by cash-out pattern


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _high_amount_flag(amount: float) -> float:
    """Binary flag: 1.0 if amount exceeds the high-amount threshold, else 0.0.

    The user rule states 'Transactions above 10,000 are high risk', so this
    is the dominant amount signal.
    """
    return 1.0 if amount > HIGH_AMOUNT_THRESHOLD else 0.0


def _amount_scale(amount: float) -> float:
    """Graduated score for very large amounts (0-1 scale).

      - 0   for amount <= 10,000
      - 1.0 for amount >= 500,000 (cap)
    """
    if amount <= HIGH_AMOUNT_THRESHOLD:
        return 0.0
    if amount >= 500_000:
        return 1.0
    return (amount - HIGH_AMOUNT_THRESHOLD) / (500_000 - HIGH_AMOUNT_THRESHOLD)


def _type_score(txn_type: str) -> float:
    """Score based on transaction type. 1.0 for risky types, 0.0 otherwise."""
    return 1.0 if txn_type in HIGH_RISK_TYPES else 0.0


def _new_destination_score(name_dest: str, seen_destinations: set) -> float:
    """Score 1.0 if the destination has never been seen before, else 0.0."""
    return 0.0 if name_dest in seen_destinations else 1.0


def _rapid_transaction_score(name_orig: str, step: int,
                             origin_history: dict) -> float:
    """Score based on how many transactions the same origin has made in the
    current time-step.  More transactions in the same step ⇒ higher score.

    Scale:
      1 txn  → 0.0
      2 txns → 0.3
      3 txns → 0.6
      4+ txns → 1.0
    """
    count = origin_history.get((name_orig, step), 0)
    if count <= 1:
        return 0.0
    if count == 2:
        return 0.3
    if count == 3:
        return 0.6
    return 1.0


def _high_amount_then_cashout_score(
    txn_type: str,
    name_orig: str,
    origin_high_amount_flag: dict,
) -> float:
    """Detects the pattern: a prior high-amount transaction from the same
    origin, followed by a CASH_OUT.  Returns 1.0 when the pattern matches."""
    if txn_type == "CASH_OUT" and origin_high_amount_flag.get(name_orig, False):
        return 1.0
    return 0.0


def compute_risk_score(
    amount: float,
    txn_type: str,
    name_orig: str,
    name_dest: str,
    step: int,
    seen_destinations: set,
    origin_history: dict,
    origin_high_amount_flag: dict,
) -> int:
    """Compute the composite risk score (0-100) for a single transaction."""
    score = 0.0
    score += WEIGHT_HIGH_AMOUNT_FLAG * _high_amount_flag(amount)
    score += WEIGHT_AMOUNT_SCALE * _amount_scale(amount)
    score += WEIGHT_TYPE * _type_score(txn_type)
    score += WEIGHT_NEW_DEST * _new_destination_score(name_dest,
                                                       seen_destinations)
    score += WEIGHT_RAPID_TXN * _rapid_transaction_score(name_orig, step,
                                                          origin_history)
    score += WEIGHT_HIGH_THEN_CASHOUT * _high_amount_then_cashout_score(
        txn_type, name_orig, origin_high_amount_flag
    )
    # Clamp to 0-100
    return int(min(max(round(score), 0), 100))


def classify_risk(score: int) -> str:
    """Map a numeric score to a risk category."""
    if score < 40:
        return "LOW"
    if score <= 70:
        return "MEDIUM"
    return "HIGH"


# ---------------------------------------------------------------------------
# Pre-processing: build look-ahead structures
# ---------------------------------------------------------------------------

def _preprocess(rows: list[dict]) -> tuple[dict, dict]:
    """Scan the full transaction list to build:

    1. origin_history  – {(nameOrig, step): count}
       How many transactions each origin made per time-step.
    2. origin_high_amount_flag – {nameOrig: bool}
       Whether the origin ever sent an amount > HIGH_AMOUNT_THRESHOLD
       *before* any CASH_OUT from that origin.  We iterate in order so
       that the flag is available when the CASH_OUT row is scored.
    """
    origin_history: dict[tuple[str, int], int] = defaultdict(int)
    origin_high_amount_flag: dict[str, bool] = {}

    for row in rows:
        step = int(row["step"])
        name_orig = row["nameOrig"]
        amount = float(row["amount"])
        txn_type = row["type"]

        origin_history[(name_orig, step)] += 1

        # Track high-amount-then-cashout pattern
        if amount > HIGH_AMOUNT_THRESHOLD and txn_type != "CASH_OUT":
            origin_high_amount_flag[name_orig] = True

    return dict(origin_history), origin_high_amount_flag


# ---------------------------------------------------------------------------
# Main pipeline
# ---------------------------------------------------------------------------

def score_transactions(input_path: str) -> list[dict]:
    """Read transactions from *input_path*, score each one, and return the
    enriched rows with ``risk_score`` and ``risk_category`` fields."""
    with open(input_path, newline="") as fh:
        reader = csv.DictReader(fh)
        rows = list(reader)

    origin_history, origin_high_amount_flag = _preprocess(rows)
    seen_destinations: set[str] = set()
    scored_rows: list[dict] = []

    for row in rows:
        step = int(row["step"])
        txn_type = row["type"]
        amount = float(row["amount"])
        name_orig = row["nameOrig"]
        name_dest = row["nameDest"]

        risk_score = compute_risk_score(
            amount=amount,
            txn_type=txn_type,
            name_orig=name_orig,
            name_dest=name_dest,
            step=step,
            seen_destinations=seen_destinations,
            origin_history=origin_history,
            origin_high_amount_flag=origin_high_amount_flag,
        )
        risk_category = classify_risk(risk_score)

        scored_row = dict(row)
        scored_row["risk_score"] = risk_score
        scored_row["risk_category"] = risk_category
        scored_rows.append(scored_row)

        # Mark destination as seen *after* scoring this transaction
        seen_destinations.add(name_dest)

    return scored_rows


def generate_report(scored_rows: list[dict], output_path: str) -> None:
    """Write the scored transactions to a CSV risk report."""
    if not scored_rows:
        return

    fieldnames = list(scored_rows[0].keys())
    with open(output_path, "w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(scored_rows)


def print_summary(scored_rows: list[dict]) -> None:
    """Print a concise summary of the risk distribution."""
    total = len(scored_rows)
    counts = defaultdict(int)
    for row in scored_rows:
        counts[row["risk_category"]] += 1

    print("=" * 60)
    print("         TRANSACTION RISK REPORT SUMMARY")
    print("=" * 60)
    print(f"  Total transactions analysed : {total}")
    for cat in ("HIGH", "MEDIUM", "LOW"):
        pct = (counts[cat] / total * 100) if total else 0
        print(f"  {cat:6s} risk transactions    : {counts[cat]:>4d}  ({pct:5.1f}%)")
    print("=" * 60)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    script_dir = os.path.dirname(os.path.abspath(__file__))
    input_csv = os.path.join(script_dir, "data", "Example1.csv")
    output_csv = os.path.join(script_dir, "data", "transaction_risk_report.csv")

    scored = score_transactions(input_csv)
    generate_report(scored, output_csv)
    print_summary(scored)
    print(f"\nDetailed report written to: {output_csv}")


if __name__ == "__main__":
    main()
