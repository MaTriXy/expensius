/*
 * Copyright (C) 2016 Mantas Varnagiris.
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

package com.mvcoding.expensius.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.mvcoding.expensius.firebase.model.FirebaseUserData
import com.mvcoding.expensius.firebase.model.toSettings
import com.mvcoding.expensius.model.AppUser
import com.mvcoding.expensius.model.AuthProvider
import com.mvcoding.expensius.model.AuthProvider.ANONYMOUS
import com.mvcoding.expensius.model.AuthProvider.GOOGLE
import com.mvcoding.expensius.model.GoogleToken
import com.mvcoding.expensius.model.NullModels.noAppUser
import com.mvcoding.expensius.model.Settings
import com.mvcoding.expensius.model.UserId
import com.mvcoding.expensius.service.AppUserService
import com.mvcoding.expensius.service.LoginService
import rx.Observable
import rx.Observable.combineLatest
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.deferredObservable
import rx.lang.kotlin.observable
import java.util.concurrent.atomic.AtomicReference

class FirebaseAppUserService : AppUserService, LoginService {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseUserSubject = BehaviorSubject<FirebaseUser?>()
    private val firebaseUserDataSubject = BehaviorSubject<FirebaseUserData?>()

    private val appUserObservable: Observable<AppUser>
    private val currentAppUser = AtomicReference<AppUser>()

    private val authStateListener = AuthStateListener { onFirebaseUserChanged(it.currentUser) }


    private var userDataListener: ValueEventListener? = null

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
        appUserObservable = combineLatest(firebaseUserSubject, firebaseUserDataSubject) { firebaseUser, firebaseUserData ->
            val settings = firebaseUserData?.settings.toSettings()
            firebaseUser?.toAppUser(settings) ?: noAppUser.withSettings(settings)
        }.distinctUntilChanged()

        appUserObservable.subscribe { currentAppUser.set(it) }
    }

    override fun appUser(): Observable<AppUser> = appUserObservable
    override fun getCurrentAppUser(): AppUser = currentAppUser.get()

    override fun loginAnonymously(): Observable<Unit> = deferredObservable {
        observable<Unit> { subscriber ->
            firebaseAuth.signInAnonymously()
                    .addOnSuccessListener {
                        subscriber.onNext(Unit)
                        subscriber.onCompleted()
                    }
                    .addOnFailureListener { subscriber.onError(it) }
        }
    }

    override fun loginWithGoogle(googleToken: GoogleToken): Observable<Unit> = appUser().first().switchMap {
        if (it.isLoggedIn()) {
            observable<Unit> { subscriber ->
                val credential = GoogleAuthProvider.getCredential(googleToken.token, null)
                firebaseAuth.currentUser!!.linkWithCredential(credential)
                        .addOnSuccessListener {
                            subscriber.onNext(Unit)
                            subscriber.onCompleted()
                        }
                        .addOnFailureListener { subscriber.onError(it) } // TODO com.google.firebase.auth.FirebaseAuthUserCollisionException: This credential is already associated with a different user account.
            }
        } else {
            observable<Unit> { subscriber ->
                val credential = GoogleAuthProvider.getCredential(googleToken.token, null)
                firebaseAuth.signInWithCredential(credential)
                        .addOnSuccessListener {
                            subscriber.onNext(Unit)
                            subscriber.onCompleted()
                        }
                        .addOnFailureListener { subscriber.onError(it) }
            }
        }
    }

    private fun onFirebaseUserChanged(firebaseUser: FirebaseUser?) {
        firebaseUserSubject.onNext(firebaseUser)
        if (firebaseUser == null) firebaseUserDataSubject.onNext(null)
        else if (userDataListener == null) setupUserDataListener(UserId(firebaseUser.uid))
    }

    private fun setupUserDataListener(userId: UserId) {
        userDataListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                firebaseUserDataSubject.onNext(dataSnapshot.getValue(FirebaseUserData::class.java))
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // TODO Handle error
            }
        }

        userDatabaseReference(userId).addValueEventListener(userDataListener)
    }

    private fun FirebaseUser.toAppUser(settings: Settings) = AppUser(UserId(uid), settings, providerData.map { it.providerId.toAuthProvider() }.toSet())
    private fun String.toAuthProvider(): AuthProvider = when (this) {
        GoogleAuthProvider.PROVIDER_ID -> GOOGLE
        else -> ANONYMOUS
    }
}