"""Explicit validation failures.

Python removes ``assert`` statements under ``-O`` or ``PYTHONOPTIMIZE``, which
would let a gate record a pass without checking anything. Gates call
``require`` instead, which always runs.
"""


class CheckFailed(Exception):
    """A validation gate observed a result it must reject."""


def require(condition, message):
    if not condition:
        raise CheckFailed(message)
