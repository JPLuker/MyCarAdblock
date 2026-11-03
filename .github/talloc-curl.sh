#!/usr/bin/env bash
# curl in front of the runner's own, while build-apps.sh builds Xonos. Every call goes to the real curl
# as it is, except one: talloc's tarball, which app/native/proot.sh of Xonos (0.1.49 and later, proot
# built from source) downloads from samba.org. That server now and then doesn't answer GitHub's
# runners ("Connection died", 3 November 2026), so other copies of the same file are tried after it,
# and one is kept only with the SHA-256 that proot.sh pins (proot.sh checks it again).
set -u
real=${REAL_CURL:?the runner\'s curl}
url=${!#}
case "$url" in
    https://www.samba.org/ftp/talloc/talloc-*.tar.gz) ;;
    *) exec "$real" "$@" ;;
esac
out=
prev=
for arg in "$@"; do
    [ "$prev" = -o ] && out=$arg
    prev=$arg
done
[ -n "$out" ] || exec "$real" "$@"
name=${url##*/}
version=${name#talloc-}
version=${version%.tar.gz}
want=$(sed -n 's/^TALLOC_SHA256=//p' "${TALLOC_PINS:?proot.sh of Xonos}")
[ -n "$want" ] || exec "$real" "$@"
# Gentoo's mirrors keep each file in a folder named after the first byte of its name's BLAKE2b.
gentoo=$(python3 -c 'import hashlib, sys; print(hashlib.blake2b(sys.argv[1].encode()).hexdigest()[:2])' "$name")
for from in "$url" \
    "https://download.samba.org/pub/talloc/$name" \
    "https://deb.debian.org/debian/pool/main/t/talloc/talloc_$version.orig.tar.gz" \
    "https://distfiles.gentoo.org/distfiles/$gentoo/$name" \
    "https://distcache.freebsd.org/ports-distfiles/$name" \
    "https://distfiles.alpinelinux.org/distfiles/edge/$name"; do
    if "$real" -fsSL --retry 2 --connect-timeout 20 --max-time 180 -o "$out" "$from" &&
        echo "$want  $out" | sha256sum -c --quiet - >/dev/null 2>&1; then
        [ "$from" = "$url" ] || echo "talloc: $name from $from, samba.org didn't answer" >&2
        exit 0
    fi
    echo "talloc: $name not from $from" >&2
done
rm -f "$out"
exit 56
