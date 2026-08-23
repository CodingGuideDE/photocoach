package com.florianhaeglsperger.photocoach.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Bruecke zu Swift: iosApp/ContentView.swift ruft das als `MainViewControllerKt.MainViewController()`
 * auf (Kotlin-Top-Level-Funktionen aus einer Datei "MainViewController.kt" landen im generierten
 * Objective-C/Swift-Header als statische Methode auf einer Klasse "MainViewControllerKt").
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
