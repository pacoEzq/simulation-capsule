#!/usr/bin/env python3
"""setup_v2.py: the compact notation for setup.txt (SPEC 4.1).

A setup.txt in the plain form is a tree: section banners, nodes indented two
spaces per level, and property lines "Key: value" under the node they belong
to. The compact form says the same thing in fewer tokens and decodes back to
the plain form byte for byte. Five rules, each undone exactly by decode():

  R1  A banner trio, a line of 60 '=', the title, a line of 60 '=',
      becomes "== TITLE ==".
  R2  Two spaces of indentation per tree level become one.
  R3  A property line "  Key: value" is joined, after a TAB, to the node
      line it follows. The plain form lists a node's properties right after
      the node, before its children, at a flat two-space indent; a property
      is never joined to a section banner.
  R4  A subtree identical to an earlier subtree of the same name becomes
      "Name ^A > P", where "A > P" is the shortest tail of the earlier
      subtree's ancestor path that names it unambiguously: unique among
      the earlier subtrees of that name, and different from the tail of
      the node's own path. A subtree whose full path and name occur more
      than once is never the target of a reference.
  R5  Three or more consecutive sibling lines that differ only in numbers,
      each number in arithmetic progression, become "@k=0..N template".
      A number that changes is written as an {a:b} slot, which holds the
      number a + k*b on line k; a number that does not change is written as
      it is, and is the same on every line.

encode() writes the notation header first and checks that decode() gives
the input back. decode() strips the header when present. decode_verified()
is the reader a validator wants: it refuses a back reference that resolves
to no earlier subtree or to more than one, and a template that does not
expand, naming the line.

Standard library only.

Usage:
    python setup_v2.py encode setup.txt [out.txt]
    python setup_v2.py decode setup_compact.txt [out.txt]
"""

import re
import sys

BANNER = "=" * 60
NUMBER = re.compile(r"-?\d+(?:\.\d+)?")
SLOT = re.compile(r"\{(-?[\d.]+):(-?[\d.]+)\}")
TEMPLATE = re.compile(r"^( *)@k=0\.\.(\d+) (.*)$")
TEMPLATE_START = re.compile(r"^ *@k=0\.\.\d+ ")
BACK_REFERENCE = re.compile(r"^( *)(.*) \^(.*)$")

HEADER_BEGIN = "[Notation of this report]\n"
HEADER_END = "[/Notation]\n"
HEADER = (
    "[Notation of this report]\n"
    "== Title == : a section banner.\n"
    "Indentation: one space per tree level.\n"
    "A TAB after a line starts a property (Key: value) that belongs to "
    "that line.\n"
    "Name ^A > P : this node repeats, unchanged, the earlier node Name found "
    "under the path A > P (the shortest path tail that names it "
    "unambiguously).\n"
    "@k=0..N template : N+1 sibling lines; on line k, each {a:b} slot holds "
    "the number a + k*b, and every number written plainly is the same on "
    "all lines.\n"
    "[/Notation]\n"
)


# ---------------------------------------------------------------------------
# R5 templates
# ---------------------------------------------------------------------------

def format_like(token, value):
    """Write value the way token was written: a decimal stays a float repr,
    an integer keeps its zero padding when it had one."""
    if "." in token:
        return repr(float(value))
    digits = token.lstrip("-")
    text = str(int(round(value)))
    negative = text.startswith("-")
    text = text.lstrip("-").zfill(len(digits) if digits.startswith("0") else 0)
    return ("-" if negative else "") + text


def expand_template(line):
    """Expand one "@k=0..N template" line into its N+1 lines."""
    match = TEMPLATE.match(line)
    if match is None:
        raise ValueError("not a template line: %r" % line)
    indent, count, template = match.group(1), int(match.group(2)), match.group(3)

    def fill(k):
        return SLOT.sub(
            lambda slot: format_like(
                slot.group(1),
                float(slot.group(1)) + k * float(slot.group(2))),
            template)

    return [indent + fill(k) for k in range(count + 1)]


# ---------------------------------------------------------------------------
# Decode
# ---------------------------------------------------------------------------

def _to_plain(lines):
    """Undo R1, R2 and R3 on lines whose back references and templates are
    already expanded."""
    plain = []
    for line in lines:
        if line.startswith("== ") and line.endswith(" =="):
            plain += [BANNER, line[3:-3], BANNER]
            continue
        if not line.strip():
            plain.append(line)
            continue
        parts = line.split("\t")
        head = parts[0]
        depth = len(head) - len(head.lstrip(" "))
        plain.append(" " * (2 * depth) + head[depth:])
        plain += ["  " + prop for prop in parts[1:]]
    return plain


def _decode_body(body, verify=False, first_line=1):
    """Decode the compact body to the plain form.

    With verify, a back reference must resolve to exactly one earlier
    registered subtree and every template must expand; anything else
    raises ValueError naming the line, counted from first_line.
    """
    expanded = []  # (source line number, line)
    for number, line in enumerate(body.split("\n"), start=first_line):
        if TEMPLATE_START.match(line):
            try:
                lines = expand_template(line)
            except ValueError as exc:
                raise ValueError("line %d: template does not expand: %s"
                                 % (number, exc))
            expanded += [(number, item) for item in lines]
        else:
            expanded.append((number, line))

    # (ancestor names, name) -> the subtree's lines, relative to its depth.
    # A later subtree under the same key replaces the earlier one in place.
    registry = {}
    registered = {}  # key -> how many subtrees were registered under it
    result = []
    stack = []  # (depth, name, index of its first line in result)

    def close(depth):
        while stack and stack[-1][0] >= depth:
            node_depth, name, start = stack.pop()
            ancestors = tuple(entry[1] for entry in stack)
            key = (ancestors, name)
            registry[key] = [line[node_depth:] for line in result[start:]]
            registered[key] = registered.get(key, 0) + 1

    for number, line in expanded:
        if not line.strip() or not line.startswith(" "):
            close(0)
            result.append(line)
            if line.strip():
                stack.append((0, line.split("\t")[0], len(result) - 1))
            continue
        depth = len(line) - len(line.lstrip(" "))
        close(depth)
        match = BACK_REFERENCE.match(line)
        if match and "\t" not in line:
            name = match.group(2)
            tail = tuple(match.group(3).split(" > "))
            keys = [key for key in registry
                    if key[1] == name and key[0][-len(tail):] == tail]
            found = [registry[key] for key in keys]
            if verify and sum(registered[key] for key in keys) != 1:
                raise ValueError(
                    "line %d: back reference %r resolves to %d earlier "
                    "subtrees, it must resolve to exactly one"
                    % (number, line.strip(),
                       sum(registered[key] for key in keys)))
            if not found:
                raise ValueError("line %d: back reference %r resolves to "
                                 "nothing" % (number, line.strip()))
            start = len(result)
            result += [" " * depth + item for item in found[-1]]
            stack.append((depth, name, start))
            continue
        stack.append((depth, line.split("\t")[0][depth:], len(result)))
        result.append(line)
    close(0)
    return "\n".join(_to_plain(result))


def strip_header(text):
    """Return (header, body). header is "" when text does not open with the
    header's first line; otherwise it is everything up to the closing line,
    which may differ from HEADER if it was edited."""
    if not text.startswith(HEADER_BEGIN):
        return "", text
    end = text.find(HEADER_END)
    if end < 0:
        raise ValueError("notation header opened but never closed with %r"
                         % HEADER_END.strip())
    end += len(HEADER_END)
    return text[:end], text[end:]


def decode(text):
    """Decode a compact setup.txt, with or without header, to the plain
    form."""
    _, body = strip_header(text)
    return _decode_body(body)


def decode_verified(text):
    """Decode, refusing anything the encoder would not have written.

    A header, when present, must be HEADER verbatim. Every back reference
    must resolve to exactly one earlier registered subtree and every
    template must expand. Raises ValueError naming the line; returns the
    plain form.
    """
    header, body = strip_header(text)
    if header and header != HEADER:
        raise ValueError("notation header differs from the one SPEC 4.1 "
                         "quotes")
    return _decode_body(body, verify=True,
                        first_line=header.count("\n") + 1)


def has_compact_marks(text):
    """True when text carries a mark only the compact form writes: a TAB,
    a template line or a line ending in a back reference."""
    for line in text.split("\n"):
        if "\t" in line or TEMPLATE_START.match(line) \
                or BACK_REFERENCE.match(line):
            return True
    return False


# ---------------------------------------------------------------------------
# Encode
# ---------------------------------------------------------------------------

def _encode_body(plain):
    if "\t" in plain or " > " in plain or " ^" in plain:
        raise ValueError("the plain form holds a TAB, ' > ' or ' ^', which "
                         "the compact form reserves")
    source = plain.split("\n")

    # R1, R2, R3: one record per node, its properties after it.
    records = []
    i = 0
    while i < len(source):
        line = source[i]
        if line == BANNER and i + 2 < len(source) and source[i + 2] == BANNER:
            records.append(["== %s ==" % source[i + 1]])
            i += 3
            continue
        if re.match(r"^  \S", line) and ": " in line and records \
                and records[-1][0].strip() \
                and not records[-1][0].startswith("== "):
            records[-1].append(line[2:])
            i += 1
            continue
        indent = len(line) - len(line.lstrip(" "))
        if indent % 2:
            raise ValueError("odd indentation, not two spaces per level: %r"
                             % line)
        records.append([" " * (indent // 2) + line[indent:]])
        i += 1
    lines = ["\t".join(record) for record in records]
    count = len(lines)

    # Tree structure: depth (-1 for a blank line), subtree end, ancestors.
    depth = [len(x) - len(x.lstrip(" ")) if x.startswith(" ")
             else (0 if x.strip() else -1) for x in lines]
    end = [0] * count
    for a in range(count):
        b = a + 1
        if depth[a] >= 0:
            while b < count and depth[b] > depth[a] \
                    and lines[b].startswith(" "):
                b += 1
        end[a] = b
    names = [x.split("\t")[0].strip() for x in lines]
    ancestors = [()] * count
    open_nodes = []
    for a in range(count):
        if depth[a] < 0:
            open_nodes = []
            continue
        while open_nodes and depth[open_nodes[-1]] >= depth[a]:
            open_nodes.pop()
        ancestors[a] = tuple(names[x] for x in open_nodes)
        open_nodes.append(a)

    # R4: back references. A subtree is registered when it closes, as the
    # decoder registers it.
    out = []
    registry = {}
    registered = {}  # key -> how many subtrees were registered under it
    pending = []  # (end, key, relative lines)
    a = 0
    while a < count:
        pending.sort()
        while pending and pending[0][0] <= a:
            _, key, relative = pending.pop(0)
            registry[key] = relative
            registered[key] = registered.get(key, 0) + 1
        d = depth[a]
        if d >= 1:
            relative = [x[d:] for x in lines[a:end[a]]]
            same_name = [k for k in registry if k[1] == names[a]]
            identical = [k for k in same_name if registry[k] == relative
                         and registered[k] == 1]
            reference = None
            if identical:
                target = identical[-1][0]
                for n in range(1, len(target) + 1):
                    tail = target[-n:]
                    if [k for k in same_name if k[0][-n:] == tail] \
                            == [identical[-1]] \
                            and ancestors[a][-n:] != tail:
                        reference = " > ".join(tail)
                        break
            key = (ancestors[a], names[a])
            if reference and len("\n".join(relative)) \
                    > len(names[a]) + len(reference) + 3:
                out.append(" " * d + names[a] + " ^" + reference)
                pending.append((end[a], key, relative))
                a = end[a]
                continue
            pending.append((end[a], key, relative))
        elif d == 0:
            pending.append((end[a], ((), names[a]), lines[a:end[a]]))
        out.append(lines[a])
        a += 1

    # R5: runs of sibling lines in arithmetic progression.
    result = []
    i = 0
    while i < len(out):
        line = out[i]
        if line.startswith(" ") and not SLOT.search(line):
            shape = NUMBER.sub("#", line)
            j = i
            while j + 1 < len(out) and NUMBER.sub("#", out[j + 1]) == shape:
                j += 1
            if j - i >= 2:
                tokens = [NUMBER.findall(out[x]) for x in range(i, j + 1)]
                pieces = re.split(NUMBER, line)
                template = pieces[0]
                for s in range(len(tokens[0])):
                    first = float(tokens[0][s])
                    step = float(tokens[1][s]) - first
                    if step == 0:
                        template += tokens[0][s]
                    else:
                        template += "{%s:%s}" % (
                            tokens[0][s],
                            repr(step) if step % 1 else str(int(step)))
                    template += pieces[s + 1]
                indent = len(line) - len(line.lstrip(" "))
                candidate = (" " * indent + "@k=0..%d " % (j - i)
                             + template[indent:])
                if expand_template(candidate) == out[i:j + 1]:
                    result.append(candidate)
                    i = j + 1
                    continue
        result.append(line)
        i += 1

    body = "\n".join(result)
    if _decode_body(body) != plain:
        raise ValueError("round trip failed: decode does not give the input "
                         "back")
    return body


def encode(plain):
    """Encode a plain setup.txt to the compact form, header first."""
    return HEADER + _encode_body(plain)


# ---------------------------------------------------------------------------
# Command line
# ---------------------------------------------------------------------------

def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) not in (2, 3) or argv[0] not in ("encode", "decode"):
        print(__doc__.split("Usage:")[1].rstrip(), file=sys.stderr)
        return 2
    mode, source = argv[0], argv[1]
    with open(source, encoding="utf-8", newline="") as handle:
        text = handle.read()
    try:
        converted = encode(text) if mode == "encode" else decode_verified(text)
    except ValueError as exc:
        print("%s: %s" % (source, exc), file=sys.stderr)
        return 1
    if len(argv) == 3:
        with open(argv[2], "w", encoding="utf-8", newline="") as handle:
            handle.write(converted)
    size_in = len(text.encode("utf-8"))
    size_out = len(converted.encode("utf-8"))
    print("%s: %d -> %d bytes (%+.1f%%)"
          % (source, size_in, size_out,
             100.0 * (size_out - size_in) / size_in if size_in else 0.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
