// Client-side port of the server's FractionalIndex.between(), used only to place a card
// optimistically while the move request is in flight. The server's key is authoritative.

const DIGITS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'

function midpoint(a: string, b: string | null): string {
  if (b !== null) {
    let n = 0
    while (n < b.length && (a[n] ?? '0') === b[n]) n++
    if (n > 0) return b.slice(0, n) + midpoint(a.slice(n), b.slice(n))
  }
  const digitA = a ? DIGITS.indexOf(a[0]) : 0
  const digitB = b !== null ? DIGITS.indexOf(b[0]) : DIGITS.length
  if (digitB - digitA > 1) return DIGITS[Math.floor((digitA + digitB + 1) / 2)]
  if (b && b.length > 1) return b.slice(0, 1)
  return DIGITS[digitA] + midpoint(a.slice(1), null)
}

export function keyBetween(lower: string | null, upper: string | null): string {
  const a = lower ?? ''
  if (upper !== null && a >= upper) return a + 'V' // inconsistent local state; server will fix it
  return midpoint(a, upper)
}
