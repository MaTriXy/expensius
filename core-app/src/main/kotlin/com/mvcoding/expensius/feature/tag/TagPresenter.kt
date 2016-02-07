/*
 * Copyright (C) 2015 Mantas Varnagiris.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.mvcoding.expensius.feature.tag

import com.mvcoding.expensius.ModelState.ARCHIVED
import com.mvcoding.expensius.feature.Presenter
import com.mvcoding.expensius.feature.tag.Tag.Companion.noTag
import rx.Observable
import rx.Observable.combineLatest
import rx.Observable.just
import java.util.*

class TagPresenter(private var tag: Tag, private val tagsProvider: TagsProvider) : Presenter<TagPresenter.View>() {
    override fun onAttachView(view: View) {
        super.onAttachView(view)

        view.showArchiveEnabled(tag != noTag)

        val idObservable = just(tag.id).filter { !it.isBlank() }.defaultIfEmpty(UUID.randomUUID().toString())
        val modelStateObservable = just(tag.modelState)
        val titleObservable = view.onTitleChanged().startWith(tag.title).doOnNext { view.showTitle(it) }.map { it.trim() }
        val colorObservable = view.onColorChanged().startWith(if (tag.color == 0) color(0x607d8b) else tag.color).doOnNext {
            view.showColor(it)
        }

        val tagObservable = combineLatest(
                idObservable,
                modelStateObservable,
                titleObservable,
                colorObservable,
                { id, modelState, title, color -> Tag(id, modelState, title, color) })
                .doOnNext { tag = it }

        unsubscribeOnDetach(view.onSave()
                                    .withLatestFrom(tagObservable, { action, tag -> tag })
                                    .filter { validate(it, view) }
                                    .doOnNext { tagsProvider.save(setOf(it)) }
                                    .subscribe { view.displayResult(it) })

        unsubscribeOnDetach(view.onArchive()
                                    .map { tag.withModelState(ARCHIVED) }
                                    .doOnNext { tagsProvider.save(setOf(it)) }
                                    .subscribe { view.displayResult(it) })
    }

    private fun validate(tag: Tag, view: View): Boolean {
        if (tag.title.isBlank()) {
            view.showTitleCannotBeEmptyError()
            return false
        }
        return true
    }

    interface View : Presenter.View {
        fun showTitle(title: String)
        fun showColor(color: Int)
        fun showTitleCannotBeEmptyError()
        fun showArchiveEnabled(archiveEnabled: Boolean)
        fun onTitleChanged(): Observable<String>
        fun onColorChanged(): Observable<Int>
        fun onArchive(): Observable<Unit>
        fun onSave(): Observable<Unit>
        fun displayResult(tag: Tag)
    }
}