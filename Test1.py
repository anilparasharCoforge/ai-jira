from flask import Flask, request, jsonify

app = Flask(__name__)

# Dummy ticket-to-status mapping (you can customize this)
TICKET_STATUS_MAP = {
    "JIRA-123": "Approved",
    "JIRA-456": "Pending",
    "JIRA-789": "Rejected"
}

@app.route('/status', methods=['GET'])
def get_status():
    ticket = request.args.get('ticket')
    if not ticket:
        return jsonify({"error": "Missing 'ticket' parameter"}), 400

    status = TICKET_STATUS_MAP.get(ticket.upper(), "Unknown")
    return jsonify({"status": status})

if __name__ == '__main__':
    app.run(debug=True)
