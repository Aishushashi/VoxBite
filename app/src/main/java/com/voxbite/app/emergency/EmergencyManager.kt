package com.voxbite.app.emergency

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import com.voxbite.app.model.EmergencyContact

class EmergencyManager(private val context: Context) {

    companion object {
        private const val TAG = "VoxBiteSOSManager"
        private const val PREFS_NAME = "voxbite_emergency"
        private const val KEY_CONTACTS = "emergency_contacts"
        private const val KEY_USER_NAME = "user_name"
    }

    // ── Contact storage (SharedPreferences, comma-separated) ──────────────
    fun saveContacts(contacts: List<EmergencyContact>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = contacts.joinToString("|") { "${it.name},${it.phone}" }
        prefs.edit().putString(KEY_CONTACTS, encoded).apply()
    }

    fun getContacts(): List<EmergencyContact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONTACTS, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull {
            val parts = it.split(",")
            if (parts.size >= 2) EmergencyContact(parts[0], parts[1]) else null
        }
    }

    fun saveUserName(name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_NAME, "Someone") ?: "Someone"
    }

    // ── SMS sender ────────────────────────────────────────────────────────
    fun sendSosMessages(location: Location?) {
        val contacts = getContacts()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured")
            return
        }

        val userName = getUserName()
        val locationText = if (location != null) {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "Location unavailable"
        }

        val message = "EMERGENCY: $userName needs help! " +
                "Location: $locationText — Sent via VoxBite"

        val smsManager = SmsManager.getDefault()
        contacts.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.phone, null, message, null, null)
                Log.d(TAG, "SOS SMS sent to ${contact.name} at ${contact.phone}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to ${contact.phone}: ${e.message}")
            }
        }
    }

    // ── Open nearest hospital in Google Maps ─────────────────────────────
    fun openNearestHospital(location: Location?) {
        val uri = if (location != null) {
            Uri.parse("geo:${location.latitude},${location.longitude}?q=hospital near me")
        } else {
            Uri.parse("geo:0,0?q=hospital near me")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        // Fallback if Maps not installed
        val chooser = if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        Log.d(TAG, "Opened hospital search in Maps")
    }

    // ── Open phone dialler pre-filled with 112 ───────────────────────────
    fun openEmergencyDialler() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}