//> using dep "co.fs2::fs2-core:3.10.2"

import fs2.*
import java.math.{MathContext, RoundingMode}
import scala.math.BigInt

type LFT = (BigInt, BigInt, BigInt, BigInt) // linear fractional transformation
type State = (LFT, BigInt)

val nats = Stream.iterate(BigInt(1))(_ + 1)

def comp(qrst: LFT, uvwx: LFT): LFT =
  val (q, r, s, t) = qrst
  val (u, v, w, x) = uvwx
  (
    q*u + r*w,
    q*v + r*x,
    s*u + t*w,
    s*v + t*x
  )

/*
 * Listing on page 5:
 *
stream :: (b->c) -> (b->c->Bool) -> (b->c->b) -> (b->a->b) -> b -> [a] -> [c]
stream next safe prod cons z (x:xs)
  = if safe z y
    then y : stream next safe prod cons (prod z y) (x:xs)
    else stream next safe prod cons (cons z x) xs
      where y = next z

-- A variant that emits more outputs, useful for debugging
streamd :: (b->c) -> (b->c->Bool) -> (b->c->b) -> (b->a->b) -> b -> [a] -> [(c, b, Bool, b)]
streamd next safe prod cons z (x:xs)
  = if safe z y
    then (y, z,  True, (prod z y)) : streamd next safe prod cons (prod z y) (x:xs)
    else (y, z, False, (cons z x)) : streamd next safe prod cons (cons z x) xs
      where y = next z
 */

def stream[A, B, C](
  next: B => C,
  safe: B => C => Boolean,
  prod: B => C => B,
  cons: B => A => B,
  z: B,
  s: Stream[Pure, A]
): Stream[Pure, C] =
  // Note: deliberately shadowing the params of the parent function to avoid accidental reference
  def go(z: B, s: Stream[Pure, A]): Pull[Pure, C, Unit] =
    s.pull.uncons1.flatMap {
      case Some(x, xs) =>
        val y: C = next(z)
        if (safe(z)(y)) {
          //println(s"y = $y, z = $z, safe(z)(y) = true, new state = ${prod(z)(y)}")
          Pull.output1(y) >> go(prod(z)(y), Stream(x) ++ xs)
        } else {
          //println(s"y = $y, z = $z, safe(z)(y) = false, x = $x, new state = ${cons(z)(x)}")
          go(cons(z)(x), xs)
        }
      case None =>
        Pull.done
    }
  go(z, s).stream

object Lamberts:
  /*
   * Listing on page 8 of the paper:
   *
  piL = stream next safe prod cons init lfts where
    init = ((0,4,1,0), 1)
    lfts = [(2*i-1, i*i, 1, 0) | i<-[1..]]
    next ((q,r,s,t),i) = floor ((q*x+r) % (s*x+t)) where x=2*i-1
    safe ((q,r,s,t),i) n = (n == floor ((q*x+2*r) % (s*x+2*t)))
      where x=5*i-2
    prod (z,i) n = (comp (10, -10*n, 0, 1) z, i)
    cons (z,i) z' = (comp z z', i+1)
  */

  val lfts: Stream[Pure, LFT] = nats.map { i =>
    (
      2 * i - 1,
      i * i,
      1,
      0
    )
  }

  val init: State = (
    (BigInt(0), BigInt(4), BigInt(1), BigInt(0)),
    BigInt(1)
  )

  def floorRatio(a: BigInt, b: BigInt): BigInt =
    (BigDecimal(a) / BigDecimal(b)).setScale(0, BigDecimal.RoundingMode.FLOOR).toBigInt

  def next(qrst: LFT, i: BigInt): BigInt =
    val (q, r, s, t) = qrst
    val x = 2 * i - 1
    floorRatio((q*x + r), (s*x + t))

  def safe(qrst: LFT, i: BigInt)(n: BigInt): Boolean =
    val (q, r, s, t) = qrst
    val x = 5 * i - 2
    n == floorRatio((q*x + 2*r), (s*x + 2*t))

  def prod(z: LFT, i: BigInt)(n: BigInt): State =
    (comp((BigInt(10), BigInt(-10) * n, BigInt(0), BigInt(1)), z), i)
   
  def cons(z: LFT, i: BigInt)(zPrime: LFT): State =
    (comp(z, zPrime), i + 1)

  val pi = stream(next.tupled, safe, prod, cons, init, lfts)

object Gospers:
  /*
   * Listing on page 9 of the paper, which contains a typo:
   *
  piG = stream next safe prod cons init lfts where
    init                 = ((1,0,0,1), 1)
    lfts                 = [let j = 3*(3*i+1)*(3*i+2)
                            in (i*(2*i-1),j*(5*i-2),0,j) | i<-[1..]]
    next ((q,r,s,t),i)   = div (q*x+5*r) (s*x+5*t) where x = 27*i+15
    safe ((q,r,s,t),i) n = (n == div (q*x+125*r) (s*x+125*t))
                           where x=675*i-216
    prod (z,i) n         = (comp (10, -10*n, 0, 1) z, i)
    cons (z,i) z'        = (comp z z', i+1)

   *
   * With the typo in `next` fixed:
   *
  piG = stream next safe prod cons init lfts where
    init                 = ((1,0,0,1), 1)
    lfts                 = [let j = 3*(3*i+1)*(3*i+2)
                            in (i*(2*i-1),j*(5*i-2),0,j) | i<-[1..]]
    next ((q,r,s,t),i)   = div (q*x+5*r) (s*x+5*t) where x = 27*i-12
    safe ((q,r,s,t),i) n = (n == div (q*x+125*r) (s*x+125*t))
                           where x=675*i-216
    prod (z,i) n         = (comp (10, -10*n, 0, 1) z, i)
    cons (z,i) z'        = (comp z z', i+1)
   */

  val lfts: Stream[Pure, LFT] = nats.map { i =>
    val j = 3 * (3 * i + 1) * (3 * i + 2)
    (
      i * (2 * i - 1),
      j * (5 * i - 2),
      0,
      j
    )
  }

  lfts.take(10).toList

  val unit: LFT = (BigInt(1), BigInt(0), BigInt(0), BigInt(1))

  val init: State = (unit, BigInt(1))

  def next(qrst: LFT, i: BigInt): BigInt =
    val (q, r, s, t) = qrst
    val x = 27 * i - 12 // Note: not 27*1+15 like in the paper
    (q*x + 5*r) / (s*x + 5*t) 

  def safe(qrst: LFT, i: BigInt)(n: BigInt): Boolean =
    val (q, r, s, t) = qrst
    val x = 675*i - 216
    n == ((q*x + 125*r) / (s*x + 125*t))

  def prod(z: LFT, i: BigInt)(n: BigInt): State =
    (comp((BigInt(10), BigInt(-10) * n, BigInt(0), BigInt(1)), z), i)
   
  def cons(z: LFT, i: BigInt)(zPrime: LFT): State =
    (comp(z, zPrime), i + 1)

  val pi = stream(next.tupled, safe, prod, cons, init, lfts)

Lamberts.pi.take(20).toList

Lamberts.pi.drop(90).take(10).toList

Lamberts.pi.drop(1000).take(10).toList

Gospers.pi.take(20).toList

Gospers.pi.drop(90).take(10).toList

Gospers.pi.drop(1000).take(10).toList

Gospers.pi.drop(10000).take(10).toList
