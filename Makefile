# The one named target CI and a contributor both run. A local set that differs from the CI set turns
# "green here, red there" into the normal state of affairs, and then neither is read.
.PHONY: check report fix

# Anchors resolve against the sibling repositories of this stack: while there is no shashki source
# tree, every path in the research points into one of them.
REPOS ?= ..

check:
	python3 scripts/backlog_index.py --check
	python3 scripts/docs_check.py
	python3 scripts/coverage_map.py --check
	python3 scripts/style_contract.py
	python3 scripts/chart_config.py --check

# Non-blocking on purpose. An anchor goes stale because of a refactor in somebody else's repository,
# and a machine cannot tell a live path from one quoted as obsolete.
#
# `bdd_report.py` takes `--repos` now that there is a source tree: without it the "automated" column
# counts a line that names a test rather than a test that exists, which is a different number.
report:
	python3 scripts/bdd_report.py --repos $(REPOS)
	python3 scripts/code_anchors.py --repos $(REPOS)

# Regenerates the index and appends the coverage-map lines you forgot. The descriptions it writes are
# placeholders; finishing them is yours.
fix:
	python3 scripts/backlog_index.py
	python3 scripts/coverage_map.py --fix
