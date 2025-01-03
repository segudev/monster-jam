package com.github.unthingable.jam.layer

import com.bitwig.extension.controller.api.Track
import com.github.unthingable.Util
import com.github.unthingable.framework.mode.CycleMode
import com.github.unthingable.framework.mode.ModeButtonCycleLayer
import com.github.unthingable.jam.Jam
import com.github.unthingable.jam.JamParameter
import com.github.unthingable.jam.PRange
import com.github.unthingable.jam.SliderBankMode
import com.github.unthingable.jam.surface.BlackSysexMagic.BarMode
import com.github.unthingable.jam.surface.JamTouchStrip

import scala.collection.mutable

trait Level:
  this: Jam =>
  lazy val levelCycle = new ModeButtonCycleLayer("LEVEL", j.level, CycleMode.Cycle) with Util:
    val paramLimits: mutable.Seq[Double] = mutable.ArrayBuffer.fill(8)(1.0)
    override val subModes = Vector(
      new SliderBankMode(
        "strips volume",
        trackBank.getItemAt,
        p => JamParameter.Regular(p.volume()),
        Seq.fill(8)(BarMode.DUAL)
      ):
        EIGHT.foreach { idx =>
          val track = trackBank.getItemAt(idx)
          track.trackType().markInterested()
          track.trackType().addValueObserver(v => if isOn then updateLimits(Some(idx, v)))
        }
        ext.preferences.limitLevel.addValueObserver(_ => if isOn then updateLimits(None))

        proxies.forindex {
          case (track, idx) =>
            val strip: JamTouchStrip = j.stripBank.strips(idx)
            track.addVuMeterObserver(128, -1, true, v => 
            if (isOn && !track.mute().get()) { // Check mute state before updating VU meter
              strip.update(v)
              })
        
            // Mute State Observer
            track.mute().addValueObserver(isMuted => {
            // If the track is muted and the volume mode is on, clear the VU meter display
            if (isMuted && isOn) { 
              strip.update(0) 
            }
            })
        }
        
        override def onActivate(): Unit =
          super.onActivate()
          // clear meter values from whatever was happening before, let VU meters self update
          j.stripBank.strips.foreach(_.update(0))
          updateLimits(None)

        override def paramRange(idx: Int): PRange = PRange(0.0, paramLimits(idx))

        def updateLimits(maybeType: Option[(Int, String)], bind: Boolean = true): Unit =
          val max      = 1.259921049894873
          val zero     = 1.0
          val minusTen = 0.6812920690579614

          maybeType match
            case Some((idx, trackType)) =>
              import com.github.unthingable.JamSettings.LimitLevels
              paramLimits.update(
                idx,
                ext.preferences.limitLevel.get() match
                  case LimitLevels.None => 1.0
                  case LimitLevels.Smart =>
                    trackType match
                      case "Group" => zero / max
                      // case "Effect" | "Master" => 1.0
                      case _ => minusTen / max
                  case LimitLevels.`0dB`   => zero / max
                  case LimitLevels.`-10dB` => minusTen / max
                  // case _        => 1.0
              )
              Util.println(f"updateLimits: $idx limit ${paramLimits(idx)}%1.2f:$trackType")
              if bind then bindWithRange(idx)
            case None =>
              EIGHT.map(idx => Some((idx, trackBank.getItemAt(idx).trackType().get()))).foreach(updateLimits(_, bind))
          end match
        end updateLimits
      ,
      new SliderBankMode(
        "strips pan",
        trackBank.getItemAt,
        p => JamParameter.Regular(p.pan()),
        Seq.fill(8)(BarMode.PAN)
      ),
    )
end Level
