#!/bin/bash
# ─────────────────────────────────────────────────────────────
# setup-agents.sh
# Sets up the Agentic Workflow Orchestrator agents in:
#   .claude/agents/  — Claude Code .agent.md format (ONLY correct location)
#
# Usage:
#   cd /path/to/your/projects-root
#   chmod +x setup-agents.sh
#   ./setup-agents.sh
# ─────────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================="
echo "  Agentic Workflow Orchestrator Setup"
echo "========================================="
echo ""

# ── Setup .claude/agents/ (Claude Code .agent.md format) ──
echo "Setting up .claude/agents/"
AGENTS_DIR="$SCRIPT_DIR/.claude/agents"
mkdir -p "$AGENTS_DIR"

# List discovered agent files
echo ""
echo "  Agent files (.agent.md):"
COUNT=0
for f in "$AGENTS_DIR"/*.agent.md; do
    [ -f "$f" ] || continue
    COUNT=$((COUNT + 1))
    NAME=$(grep "^name:" "$f" | head -1 | sed 's/name: //')
    echo "    $(basename "$f") → $NAME"
done

if [ "$COUNT" -eq 0 ]; then
    echo "    (none found — copy your .agent.md files into $AGENTS_DIR/)"
fi

echo ""
echo "========================================="
echo "  Setup Complete"
echo "========================================="
echo ""
echo "Agents location: $AGENTS_DIR/*.agent.md"
echo "Total agents:    $COUNT"
echo ""
echo "IMPORTANT: Claude Code discovers agents ONLY from .claude/agents/"
echo "           Do NOT place agents in .github/agents/ (that's for GitHub Copilot)"
echo ""
echo "To use in Claude Code, type:"
echo '  @workflow-orchestrator start the workflow menu'
echo ""
echo "Or reference any agent directly:"
echo '  @requirements-agent create a Jira story'
echo '  @testing-agent run the test suite'
echo '  @security-agent scan for vulnerabilities'
echo ""
echo "Done."
