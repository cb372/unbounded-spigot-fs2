# Unbounded Spigots in fs2

An implementation of [Unbounded Spigot Algorithms for the Digits of
Pi](https://www.cs.ox.ac.uk/people/jeremy.gibbons/publications/spigot.pdf)
(Jeremy Gibbons, 2005) in Scala using fs2.

## The paper

The paper first introduces a generic higher-order function for constructing
infinite streams. It then uses this function to implement 3 algorithms for
streaming the digits of Pi:

1. Unnamed. He doesn't give a name to the first algorithm
2. Lambert's expression
3. Gosper's series

## Gosper's series

I decided to try Gosper's series first, because the paper states that it is the
most efficient ("yielding more than one decimal digit for each term").

After implementing it with fs2, I got this:

```scala
Gospers.pi.take(10).toList
// res1: List[BigInt] = List(3, 0, 5, 0, 9, -5, 0, -25, 0, -41)
```

Errrrrrr......

I had no way to debug this. I had to assume it was a bug somewhere in my code,
but I didn't understand the maths of the paper well enough to know where I'd
gone wrong.

As a sanity check, i tried copy-pasting the Haskell code from the paper into
`ghci`:

```
ghci> intercalate ", " (map show $ take 20 piG)
"3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 6, -1, -1, 7, 9, 3, 2, 3, 8, 4"
```

Ummmmmm......

Interestingly, this gives a *different* wrong answer to my Scala code.

Either there's a typo in the paper, or I somehow copy-pasted it wrong, or the
code used to work in the olden days (2005) when the paper was written, but
doesn't work with modern versions of Haskell.

## Lambert's expression

Let's give up on Gosper's series and try Lambert's expression instead.

This time, to get a baseline to work against, I started by copy-pasting the
Haskell code from the paper into `ghci`;

```
ghci> intercalate ", " (map show $ take 50 piL)
"3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5, 8, 9, 7, 9, 3, 2, 3, 8, 4, 6, 2, 6, 4, 3, 3, 8, 3, 2, 7, 9, 5, 0, 2, 8, 8, 4, 1, 9, 7, 1, 6, 9, 3, 9, 9, 3, 7, 5, 1"
```

That's more like it! So the paper is not totally bogus.

How about my Scala implementation?

```scala
Lamberts.pi.take(10).toList
// res3: List[BigInt] = List(3, -8, 21, 65, -42, 141, -104, 316, -139, -269)
```

Hmmmmmm..... time to start debugging.

At least this time I know the Scala code works, so I can compare my
implementation with that, one function at a time.

Fixed a couple of bugs in BigDecimal rounding and usage of fs2 Pull, and
Lambert's expression worked!

```scala
Lamberts.pi.take(20).toList
// res0: List[BigInt] = List(3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5, 8, 9, 7, 9, 3, 2, 3, 8, 4)

Lamberts.pi.drop(90).take(10).toList
// res1: List[BigInt] = List(5, 3, 4, 2, 1, 1, 7, 0, 6, 7)

Lamberts.pi.drop(1000).take(10).toList
// res2: List[BigInt] = List(9, 3, 8, 0, 9, 5, 2, 5, 7, 2)
```

## Back to Gosper's series

With my bugfixes in place, Gosper's series is still wrong, but it's looking a
lot healthier.

```scala
Gospers.pi.take(20).toList
// res3: List[BigInt] = List(3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 6, 0, -10, -2, 0, -6,
-7, -6, -1, -5)
```

A little more debugging to do...
