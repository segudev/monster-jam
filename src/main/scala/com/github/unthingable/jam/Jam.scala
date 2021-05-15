package com.github.unthingable.jam

import com.bitwig.extension.callback.BooleanValueChangedCallback
import com.bitwig.extension.controller.api.{BooleanValue, HardwareAction, HardwareActionBindable, HardwareActionBinding, Scene, SceneBank, Scrollable, SettableBooleanValue, TrackBank}
import com.github.unthingable.MonsterJamExt
import com.github.unthingable.jam.surface.{JamColor, JamOnOffButton, JamRgbButton, JamSurface, NIColorUtil}



class Jam(val ext: MonsterJamExt) {
  // wire stuff
  val j = new JamSurface(ext)

  // wire buttons
  {
    val trackBank: TrackBank = ext.host.createMainTrackBank(8,8,8)
    val sceneBank: SceneBank = trackBank.sceneBank()
    val masterTrack = ext.host.createMasterTrack(8)

    sceneBank.canScrollForwards.markInterested()
    sceneBank.canScrollBackwards.markInterested()
    sceneBank.itemCount().markInterested()

    trackBank.cursorIndex().markInterested()
    trackBank.setSkipDisabledItems(true)
    //sceneBank.setIndication(true)

    // wire scene buttons
    for (i <- j.sceneButtons.indices) {
      val btn: JamRgbButton = j.sceneButtons(i)
      val scene: Scene = sceneBank.getScene(i)
      scene.color.markInterested()
      scene.exists.markInterested()
      btn.light.setColorSupplier(scene.color())
      // incomplete
      btn.button.pressedAction().addBinding(scene.launchAction())
    }

    // wire track group buttons
    for (col <- j.groupButtons.indices) {
      val btn = j.groupButtons(col)
      val track = trackBank.getItemAt(col)
      track.clipLauncherSlotBank().setIndication(true)

      track.color().markInterested()
      track.exists().markInterested()
      btn.light.setColorSupplier(track.color())

      track.addIsSelectedInEditorObserver { v =>
        btn.jamLight.updatedColorState = btn.jamLight.updatedColorState.copy(brightness = if (v) 2 else 0)
      }

      track.playingNotes().markInterested()
      track.playingNotes().addValueObserver { notes =>
        btn.jamLight.sendColor(btn.jamLight.updatedColorState.copy(brightness = if (notes.nonEmpty) 2 else btn.jamLight.updatedColorState.brightness))
      }

      // wire clips to matrix
      val clips = track.clipLauncherSlotBank()
      for (row <- 0 until 8) {
        val btn = j.matrix(row)(col)
        val clip = clips.getItemAt(row)
        clip.color().markInterested()
        clips.exists().markInterested()
        btn.light.setColorSupplier(clip.color())
      }
    }

    // wire strips
    j.stripBank.flushColors()
    for (i <- j.stripBank.strips.indices) {
      val strip = j.stripBank.strips(i)
      val track = trackBank.getItemAt(i)
      track.volume().markInterested()
      track.exists().markInterested()
      val b = strip.slider.setBindingWithRange(track.volume(), 0, 1)
      //strip.sliderRel.addBinding(track.volume())

      val touchP = ext.host.createAction(() => {
        val current = track.volume().get
        val stripValue = strip.slider.value().get()
        //b.setNormalizedRange(
        //  Math.max(current - 0.05, 0.0),
        //  Math.min(current + 0.05, 1))
        strip.setOffsetCallback { v =>
          val offset = (v - stripValue) * 0.2
          track.volume().set(current + offset)
        }
      }, () => "shift")
      val touchR = ext.host.createAction(() => {
        strip.clearOffsetCallback()
      }
        , () => "shift")

      j.Modifiers.shiftPressed.addOne { () =>
        //strip.slider.clearBindings()
        //track.exists().markInterested()
        //strip.sliderRel.setBindingWithRangeAndSensitivity(track.volume(),0,1, 0.5)

        //b.setSensitivity(0.1)

        strip.slider.clearBindings()

        strip.button.pressedAction().addBinding(touchP)
        strip.button.releasedAction().addBinding(touchR)
      }
      j.Modifiers.shiftReleased.addOne { () =>
        //strip.sliderRel.clearBindings()
        //track.exists().markInterested()
        //strip.slider.setBindingWithRange(track.volume(), 0, 1)

        strip.button.pressedAction().clearBindings()
        strip.button.releasedAction().clearBindings()
        strip.slider.setBinding(track.volume())
        //b.setNormalizedRange(0, 1)
        //b.setSensitivity(1.0)
      }

      track.exists().addValueObserver(j.stripBank.setActive(i, _))
      track.volume().value().markInterested()
      track.volume().value().addValueObserver(128, j.stripBank.setValue(i, _)) // move fader dot
      //track.volume().value().addValueObserver(128, strip.update) // move the fader dot

      track.addVuMeterObserver(128, -1, true, strip.update)
      //track.unsubscribe()
      //track.subscribe()
      //track.addVuMeterObserver(128, -1, true, _ => ())

      //strip.light.setColorSupplier(track.color())
      track.color().markInterested()
      track.color().addValueObserver((r,g,b) => j.stripBank.setColor(i, NIColorUtil.convertColor(r,g,b)))

      //strip.slider.value().markInterested()
      //strip.slider.value().addValueObserver(track.volume().set(_))
    }
    //trackBank.unsubscribe()

    // wire dpad
    // this behavior ls always the same, can wire it here directly
    Seq(
      j.dpad.left -> trackBank.canScrollBackwards,
      j.dpad.right -> trackBank.canScrollForwards,
      j.dpad.up -> sceneBank.canScrollBackwards,
      j.dpad.down -> sceneBank.canScrollForwards
    ) foreach { case (b: JamOnOffButton, e: BooleanValue) =>
      e.markInterested()
      b.light.isOn.setValueSupplier(e)
    }

    def scroll(forward: Boolean, target: Scrollable): HardwareActionBindable = {
      ext.host.createAction(() =>
        (j.Modifiers.Shift, forward) match {
          case (false, true) => target.scrollPageForwards()
          case (false, false) => target.scrollPageBackwards()
          case (true, true) => target.scrollForwards()
          case (true, false) => target.scrollBackwards()
        }, () => s"scroll_$forward")
    }

    j.dpad.left.button.pressedAction.addBinding(scroll(false, trackBank))
    j.dpad.right.button.pressedAction.addBinding(scroll(true, trackBank))
    j.dpad.up.button.pressedAction.addBinding(scroll(false, sceneBank))
    j.dpad.down.button.pressedAction.addBinding(scroll(true, sceneBank))

    // meters
    masterTrack.addVuMeterObserver(128, 0, true, j.levelMeter.uL)
    masterTrack.addVuMeterObserver(128, 1, true, j.levelMeter.uR)
  }

  // layer experiment
  {
    //val enc: MidiInfo = ext.xmlMap.wheel("EncBrowse", ext.xmlMap.masterElems)
    //val knob: RelativeHardwareKnob = ext.hw.createRelativeHardwareKnob(enc.id)
    //
    //knob.setAdjustValueMatcher(ext.midiIn.createRelative2sComplementCCValueMatcher(
    //  enc.channel, enc.event.value, 127))
    //knob.setStepSize(1 / 127.0)

    val navLayer = ModeLayer("navlayer", Map(j.encoder.turn -> ext.host.createRelativeHardwareControlStepTarget(
      ext.transport.fastForwardAction(),
      ext.transport.rewindAction())))

    LayerStack.push(navLayer)

    val swingLayer = ModeLayer("swinglayer", Map(j.encoder.turn -> ext.host.createRelativeHardwareControlStepTarget(
      ext.binding(() => ext.transport.increaseTempo(1,64*4), "inc tempo"),
      ext.binding(() => ext.transport.increaseTempo(-1,64*4), "dec tempo"))))

    val swing = j.swing
    swing.button.pressedAction().addBinding(ext.binding(() => LayerStack.push(swingLayer), "push swing"))
    swing.button.releasedAction().addBinding(ext.binding(() => LayerStack.pop(swingLayer), "pop swing"))
  }
}
