"""
Anomaly Detection Engine for Financial Transactions.

Analyzes transaction sequences and detects suspicious patterns including
repeated high-value transactions, transfer-to-cash-out chains, sudden
amount spikes, and behavioral anomalies.

Since each customer in the dataset has a single transaction, the engine
also performs cross-customer analysis by examining destination-account
clustering, linked transaction chains, and population-level statistics.
"""

import csv
import os
from collections import defaultdict

# ─── Configuration ───────────────────────────────────────────────────────────

HIGH_VALUE_THRESHOLD = 10000
SPIKE_MULTIPLIER = 3.0
RAPID_BURST_MIN_COUNT = 3
SEVERITY_THRESHOLDS = {"low": 1, "medium": 3, "high": 5}

INPUT_PATH = os.path.join(os.path.dirname(__file__), "data", "Example1.csv")
OUTPUT_PATH = os.path.join(
    os.path.dirname(__file__), "data", "anomaly_report.csv"
)


# ─── Data Loading ────────────────────────────────────────────────────────────

def load_transactions(path):
    """Load transactions from CSV and return a list of dicts."""
    transactions = []
    with open(path, newline="") as f:
        reader = csv.DictReader(f)
        for idx, row in enumerate(reader):
            row["step"] = int(row["step"])
            row["amount"] = float(row["amount"])
            row["oldbalanceOrg"] = float(row["oldbalanceOrg"])
            row["newbalanceOrig"] = float(row["newbalanceOrig"])
            row["oldbalanceDest"] = float(row["oldbalanceDest"])
            row["newbalanceDest"] = float(row["newbalanceDest"])
            row["isFraud"] = int(row["isFraud"])
            row["isFlaggedFraud"] = int(row["isFlaggedFraud"])
            row["_row_order"] = idx
            transactions.append(row)
    return transactions


def build_indexes(transactions):
    """Build lookup indexes for cross-customer analysis."""
    by_customer = defaultdict(list)
    by_destination = defaultdict(list)
    by_type = defaultdict(list)

    for txn in transactions:
        by_customer[txn["nameOrig"]].append(txn)
        by_destination[txn["nameDest"]].append(txn)
        by_type[txn["type"]].append(txn)

    return by_customer, by_destination, by_type


def compute_population_stats(transactions):
    """Compute population-level statistics for spike detection."""
    if not transactions:
        return {"mean": 0, "type_means": {}}

    total = sum(t["amount"] for t in transactions)
    mean = total / len(transactions)

    amounts_by_type = defaultdict(list)
    for t in transactions:
        amounts_by_type[t["type"]].append(t["amount"])

    type_means = {}
    for txn_type, amounts in amounts_by_type.items():
        type_means[txn_type] = sum(amounts) / len(amounts)

    return {"mean": mean, "type_means": type_means}


# ─── Detection Rule 1: Repeated High-Value Transactions ─────────────────────

def detect_repeated_high_value(transactions, by_destination):
    """
    Identify clusters of high-value transactions sharing the same
    destination account, and individual extreme-value transactions.
    """
    anomalies = []

    # 1a: Multiple high-value transactions targeting the same destination
    for dest, txns in by_destination.items():
        high_value_txns = [
            t for t in txns if t["amount"] > HIGH_VALUE_THRESHOLD
        ]
        if len(high_value_txns) >= 2:
            total_amount = sum(t["amount"] for t in high_value_txns)
            senders = [t["nameOrig"] for t in high_value_txns]
            anomalies.append({
                "customers": senders,
                "rule": (
                    "Repeated High-Value Transactions to Same Destination"
                ),
                "transactions": high_value_txns,
                "explanation": (
                    f"{len(high_value_txns)} high-value transactions "
                    f"(>{HIGH_VALUE_THRESHOLD}) sent to destination "
                    f"{dest}. "
                    f"Total amount: {total_amount:,.2f}. "
                    f"Pattern: "
                    + " -> ".join(
                        "HIGH_VALUE" for _ in high_value_txns
                    )
                    + ". "
                    f"Senders: {', '.join(senders)}. "
                    f"Multiple large transfers to the same account "
                    f"suggest potential money aggregation or layering."
                ),
                "signal_strength": len(high_value_txns),
            })

    # 1b: Individually extreme transactions (> 5x high-value threshold)
    for txn in transactions:
        if txn["amount"] > HIGH_VALUE_THRESHOLD * 5:
            anomalies.append({
                "customers": [txn["nameOrig"]],
                "rule": "Extreme High-Value Transaction",
                "transactions": [txn],
                "explanation": (
                    f"Single transaction of {txn['amount']:,.2f} exceeds "
                    f"5x the high-value threshold "
                    f"({HIGH_VALUE_THRESHOLD * 5:,}). "
                    f"Type: {txn['type']}. "
                    f"From {txn['nameOrig']} to {txn['nameDest']}. "
                    f"Extremely large transactions warrant immediate "
                    f"review."
                ),
                "signal_strength": 2,
            })

    return anomalies


# ─── Detection Rule 2: Transfer Followed by Cash-Out ────────────────────────

def detect_transfer_then_cashout(transactions, by_destination):
    """
    Detect TRANSFER -> CASH_OUT patterns by linking transactions through
    shared destination accounts and sequential ordering.
    """
    anomalies = []
    sorted_txns = sorted(
        transactions, key=lambda t: (t["step"], t["_row_order"])
    )

    # 2a: Same destination receives TRANSFER(s) and CASH_OUT
    dest_transfers = defaultdict(list)
    dest_cashouts = defaultdict(list)
    for txn in transactions:
        if txn["type"] == "TRANSFER":
            dest_transfers[txn["nameDest"]].append(txn)
        elif txn["type"] == "CASH_OUT":
            dest_cashouts[txn["nameDest"]].append(txn)

    for dest in set(dest_transfers.keys()) & set(dest_cashouts.keys()):
        transfers = dest_transfers[dest]
        cashouts = dest_cashouts[dest]
        all_txns = sorted(
            transfers + cashouts, key=lambda t: t["_row_order"]
        )
        total_transfer = sum(t["amount"] for t in transfers)
        total_cashout = sum(t["amount"] for t in cashouts)
        types_seq = [t["type"] for t in all_txns]

        anomalies.append({
            "customers": list(set(t["nameOrig"] for t in all_txns)),
            "rule": "Transfer Followed by Cash-Out (Same Destination)",
            "transactions": all_txns,
            "explanation": (
                f"Destination account {dest} received "
                f"{len(transfers)} TRANSFER(s) totaling "
                f"{total_transfer:,.2f} and {len(cashouts)} CASH_OUT(s) "
                f"totaling {total_cashout:,.2f}. "
                f"Sequence: {' -> '.join(types_seq)}. "
                f"This pattern suggests funds are being moved through "
                f"an intermediary account and then cashed out."
            ),
            "signal_strength": len(transfers) + len(cashouts),
        })

    # 2b: Sequential TRANSFER -> CASH_OUT in chronological order
    for i in range(len(sorted_txns) - 1):
        curr = sorted_txns[i]
        nxt = sorted_txns[i + 1]
        if curr["type"] == "TRANSFER" and nxt["type"] == "CASH_OUT":
            time_gap = nxt["step"] - curr["step"]
            anomalies.append({
                "customers": [curr["nameOrig"], nxt["nameOrig"]],
                "rule": "Sequential Transfer Then Cash-Out",
                "transactions": [curr, nxt],
                "explanation": (
                    f"TRANSFER of {curr['amount']:,.2f} by "
                    f"{curr['nameOrig']} immediately followed by "
                    f"CASH_OUT of {nxt['amount']:,.2f} by "
                    f"{nxt['nameOrig']} within {time_gap} time step(s). "
                    f"Rapid TRANSFER -> CASH_OUT sequences are a classic "
                    f"indicator of money laundering."
                ),
                "signal_strength": 2 if time_gap == 0 else 1,
            })

    # 2c: TRANSFER -> TRANSFER -> CASH_OUT triples
    for i in range(len(sorted_txns) - 2):
        t1 = sorted_txns[i]
        t2 = sorted_txns[i + 1]
        t3 = sorted_txns[i + 2]
        if (t1["type"] == "TRANSFER"
                and t2["type"] == "TRANSFER"
                and t3["type"] == "CASH_OUT"):
            time_span = t3["step"] - t1["step"]
            total = t1["amount"] + t2["amount"] + t3["amount"]
            customers = list(set([
                t1["nameOrig"], t2["nameOrig"], t3["nameOrig"]
            ]))
            anomalies.append({
                "customers": customers,
                "rule": "Transfer Chain Then Cash-Out",
                "transactions": [t1, t2, t3],
                "explanation": (
                    f"TRANSFER -> TRANSFER -> CASH_OUT sequence. "
                    f"Amounts: {t1['amount']:,.2f} -> "
                    f"{t2['amount']:,.2f} -> {t3['amount']:,.2f}. "
                    f"Total: {total:,.2f}. "
                    f"Time span: {time_span} step(s). "
                    f"Multi-hop transfer chains ending in cash-out "
                    f"indicate layered fund movement."
                ),
                "signal_strength": 3,
            })

    return anomalies


# ─── Detection Rule 3: Sudden Increase in Transaction Amounts ───────────────

def detect_sudden_spikes(transactions, stats):
    """
    Compare each transaction against population-level averages for
    its transaction type. Flag amounts exceeding SPIKE_MULTIPLIER x
    the type average.
    """
    anomalies = []
    type_means = stats["type_means"]
    global_mean = stats["mean"]

    for txn in transactions:
        txn_type = txn["type"]
        type_avg = type_means.get(txn_type, global_mean)

        if type_avg > 0 and txn["amount"] > SPIKE_MULTIPLIER * type_avg:
            ratio = txn["amount"] / type_avg
            anomalies.append({
                "customers": [txn["nameOrig"]],
                "rule": "Sudden Amount Spike",
                "transactions": [txn],
                "explanation": (
                    f"Transaction amount {txn['amount']:,.2f} is "
                    f"{ratio:.1f}x the average {txn_type} amount of "
                    f"{type_avg:,.2f}. "
                    f"Pattern: NORMAL -> NORMAL -> SPIKE (relative to "
                    f"population). "
                    f"This indicates a transaction significantly "
                    f"exceeding typical values for this type."
                ),
                "signal_strength": min(
                    3, max(1, int(ratio / SPIKE_MULTIPLIER))
                ),
            })

        # Also compare against overall global mean
        if (global_mean > 0
                and txn["amount"] > SPIKE_MULTIPLIER * global_mean
                and (type_avg <= 0
                     or txn["amount"] <= SPIKE_MULTIPLIER * type_avg)):
            ratio = txn["amount"] / global_mean
            anomalies.append({
                "customers": [txn["nameOrig"]],
                "rule": "Amount Spike vs Global Average",
                "transactions": [txn],
                "explanation": (
                    f"Transaction amount {txn['amount']:,.2f} is "
                    f"{ratio:.1f}x the global average of "
                    f"{global_mean:,.2f} across all transaction types. "
                    f"While within norms for {txn_type} transactions, "
                    f"this amount is anomalous in the broader context."
                ),
                "signal_strength": 1,
            })

    return anomalies


# ─── Detection Rule 4: Behavioral Anomalies ─────────────────────────────────

def detect_behavioral_anomalies(transactions, by_destination):
    """
    Detect sudden type changes, rapid bursts of activity,
    and unusual transaction ordering patterns.
    """
    anomalies = []
    sorted_txns = sorted(
        transactions, key=lambda t: (t["step"], t["_row_order"])
    )

    # 4a: Sudden type change between consecutive transactions
    suspicious_transitions = {
        ("PAYMENT", "CASH_OUT"),
        ("PAYMENT", "TRANSFER"),
        ("DEBIT", "CASH_OUT"),
        ("DEBIT", "TRANSFER"),
        ("TRANSFER", "DEBIT"),
        ("CASH_OUT", "TRANSFER"),
        ("CASH_OUT", "DEBIT"),
    }
    for i in range(len(sorted_txns) - 1):
        curr = sorted_txns[i]
        nxt = sorted_txns[i + 1]
        pair = (curr["type"], nxt["type"])
        if pair in suspicious_transitions:
            time_gap = nxt["step"] - curr["step"]
            anomalies.append({
                "customers": [curr["nameOrig"], nxt["nameOrig"]],
                "rule": "Sudden Type Change",
                "transactions": [curr, nxt],
                "explanation": (
                    f"Abrupt transaction type change: {pair[0]} -> "
                    f"{pair[1]} in consecutive transactions. "
                    f"Amounts: {curr['amount']:,.2f} -> "
                    f"{nxt['amount']:,.2f}. "
                    f"Time gap: {time_gap} step(s). "
                    f"Sudden behavioral shifts in transaction flow may "
                    f"indicate account compromise or coordinated fraud."
                ),
                "signal_strength": 2 if time_gap == 0 else 1,
            })

    # 4b: Rapid bursts - multiple transactions to same dest in same step
    for dest, txns in by_destination.items():
        step_groups = defaultdict(list)
        for txn in txns:
            step_groups[txn["step"]].append(txn)
        for step, group in step_groups.items():
            if len(group) >= RAPID_BURST_MIN_COUNT:
                total_amount = sum(t["amount"] for t in group)
                senders = [t["nameOrig"] for t in group]
                anomalies.append({
                    "customers": senders,
                    "rule": "Rapid Burst to Same Destination",
                    "transactions": list(group),
                    "explanation": (
                        f"{len(group)} transactions to destination "
                        f"{dest} in a single time step (step {step}). "
                        f"Total: {total_amount:,.2f}. "
                        f"Types: "
                        + ", ".join(t["type"] for t in group)
                        + ". "
                        f"Senders: {', '.join(senders)}. "
                        f"Rapid coordinated transfers may indicate "
                        f"smurfing or structuring."
                    ),
                    "signal_strength": min(
                        3, len(group) - RAPID_BURST_MIN_COUNT + 1
                    ),
                })

    # 4c: Zero-balance origin making large transfers
    for txn in transactions:
        if (txn["oldbalanceOrg"] == 0
                and txn["amount"] > 0
                and txn["type"] in ("TRANSFER", "CASH_OUT")):
            anomalies.append({
                "customers": [txn["nameOrig"]],
                "rule": "Zero-Balance Origin Transfer",
                "transactions": [txn],
                "explanation": (
                    f"Account {txn['nameOrig']} initiated a "
                    f"{txn['type']} of {txn['amount']:,.2f} with a "
                    f"starting balance of 0. "
                    f"Destination: {txn['nameDest']}. "
                    f"Transactions from zero-balance accounts are "
                    f"highly suspicious and may indicate synthetic "
                    f"identity fraud or system manipulation."
                ),
                "signal_strength": 2,
            })

    # 4d: Account drain - completely empties the account
    for txn in transactions:
        if (txn["oldbalanceOrg"] > 0
                and txn["newbalanceOrig"] == 0
                and txn["type"] in ("CASH_OUT", "TRANSFER")
                and txn["amount"] > HIGH_VALUE_THRESHOLD):
            anomalies.append({
                "customers": [txn["nameOrig"]],
                "rule": "High-Value Account Drain",
                "transactions": [txn],
                "explanation": (
                    f"Account {txn['nameOrig']} was fully drained via "
                    f"{txn['type']} of {txn['amount']:,.2f}. "
                    f"Previous balance: {txn['oldbalanceOrg']:,.2f}, "
                    f"new balance: 0. "
                    f"Complete account drainage through a high-value "
                    f"transaction is a strong fraud indicator."
                ),
                "signal_strength": 3,
            })

    # 4e: Destination balance anomaly
    for txn in transactions:
        if txn["type"] in ("TRANSFER", "CASH_OUT"):
            expected_dest = txn["oldbalanceDest"] + txn["amount"]
            actual_dest = txn["newbalanceDest"]
            if actual_dest > 0 and expected_dest > 0:
                diff = abs(expected_dest - actual_dest)
                if diff > 1.0 and diff / expected_dest > 0.01:
                    anomalies.append({
                        "customers": [txn["nameOrig"]],
                        "rule": "Destination Balance Mismatch",
                        "transactions": [txn],
                        "explanation": (
                            f"Destination {txn['nameDest']} balance "
                            f"anomaly: expected {expected_dest:,.2f} "
                            f"after receiving {txn['amount']:,.2f} but "
                            f"actual balance is {actual_dest:,.2f} "
                            f"(difference: {diff:,.2f}). "
                            f"Balance mismatches may indicate concurrent "
                            f"unauthorized withdrawals or system fraud."
                        ),
                        "signal_strength": 1,
                    })

    return anomalies


# ─── Anomaly Scoring and Aggregation ─────────────────────────────────────────

def compute_severity(total_signal_strength):
    """Map combined signal strength to a severity label."""
    if total_signal_strength >= SEVERITY_THRESHOLDS["high"]:
        return "high"
    elif total_signal_strength >= SEVERITY_THRESHOLDS["medium"]:
        return "medium"
    else:
        return "low"


# ─── Report Generation ───────────────────────────────────────────────────────

def format_transaction_detail(txn):
    """Format a single transaction into a readable string."""
    return (
        f"[step={txn['step']} type={txn['type']} "
        f"amount={txn['amount']:,.2f} "
        f"from={txn['nameOrig']} to={txn['nameDest']}]"
    )


def generate_report(transactions):
    """Run all detection rules and aggregate into per-customer report."""
    by_customer, by_destination, by_type = build_indexes(transactions)
    stats = compute_population_stats(transactions)

    all_anomalies = []
    all_anomalies.extend(
        detect_repeated_high_value(transactions, by_destination)
    )
    all_anomalies.extend(
        detect_transfer_then_cashout(transactions, by_destination)
    )
    all_anomalies.extend(detect_sudden_spikes(transactions, stats))
    all_anomalies.extend(
        detect_behavioral_anomalies(transactions, by_destination)
    )

    # Aggregate anomalies per customer for severity scoring
    customer_signal_totals = defaultdict(int)
    for anomaly in all_anomalies:
        for cust in anomaly["customers"]:
            customer_signal_totals[cust] += anomaly["signal_strength"]

    # Build report rows
    report_rows = []
    for anomaly in all_anomalies:
        for customer_id in sorted(set(anomaly["customers"])):
            total_signal = customer_signal_totals[customer_id]
            severity = compute_severity(total_signal)

            txn_details = "; ".join(
                format_transaction_detail(t)
                for t in anomaly["transactions"]
            )
            timestamps = ", ".join(
                str(t["step"]) for t in anomaly["transactions"]
            )

            report_rows.append({
                "customer_id": customer_id,
                "anomaly_rule": anomaly["rule"],
                "sequence_pattern": " -> ".join(
                    t["type"] for t in anomaly["transactions"]
                ),
                "explanation": anomaly["explanation"],
                "timestamps": timestamps,
                "transaction_details": txn_details,
                "signal_strength": anomaly["signal_strength"],
                "overall_severity": severity,
            })

    # Sort by severity (high first), then customer_id
    severity_order = {"high": 0, "medium": 1, "low": 2}
    report_rows.sort(
        key=lambda r: (
            severity_order[r["overall_severity"]], r["customer_id"]
        )
    )

    return report_rows


def write_csv_report(report_rows, output_path):
    """Write the anomaly report to a CSV file."""
    fieldnames = [
        "customer_id",
        "anomaly_rule",
        "sequence_pattern",
        "explanation",
        "timestamps",
        "transaction_details",
        "signal_strength",
        "overall_severity",
    ]
    with open(output_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(report_rows)


def print_summary(report_rows):
    """Print a summary of the anomaly report to stdout."""
    if not report_rows:
        print("No anomalies detected.")
        return

    customers = set(r["customer_id"] for r in report_rows)
    severity_counts = defaultdict(int)
    rule_counts = defaultdict(int)
    for row in report_rows:
        severity_counts[row["overall_severity"]] += 1
        rule_counts[row["anomaly_rule"]] += 1

    print("=" * 70)
    print("ANOMALY DETECTION REPORT SUMMARY")
    print("=" * 70)
    print(f"Total anomalous sequences detected: {len(report_rows)}")
    print(f"Customers with anomalies: {len(customers)}")
    print()
    print("Severity Distribution:")
    for sev in ["high", "medium", "low"]:
        count = severity_counts.get(sev, 0)
        print(f"  {sev.upper():8s}: {count}")
    print()
    print("Anomaly Rule Breakdown:")
    for rule, count in sorted(rule_counts.items(), key=lambda x: -x[1]):
        print(f"  {rule}: {count}")
    print("=" * 70)


# ─── Main ────────────────────────────────────────────────────────────────────

def main():
    print(f"Loading transactions from {INPUT_PATH}...")
    transactions = load_transactions(INPUT_PATH)
    print(f"Loaded {len(transactions)} transactions.")

    by_customer, _, _ = build_indexes(transactions)
    print(f"Found {len(by_customer)} unique customers.")
    print()

    report_rows = generate_report(transactions)

    write_csv_report(report_rows, OUTPUT_PATH)
    print(f"Anomaly report written to {OUTPUT_PATH}")
    print()

    print_summary(report_rows)


if __name__ == "__main__":
    main()
