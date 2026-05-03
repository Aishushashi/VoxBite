package com.voxbite.app.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.voxbite.app.R
import com.voxbite.app.emergency.EmergencyManager
import com.voxbite.app.model.EmergencyContact

class EmergencySetupActivity : AppCompatActivity() {

    private lateinit var manager: EmergencyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_setup)
        manager = EmergencyManager(this)

        val etName = findViewById<EditText>(R.id.etUserName)
        val etContact1Name = findViewById<EditText>(R.id.etContact1Name)
        val etContact1Phone = findViewById<EditText>(R.id.etContact1Phone)
        val etContact2Name = findViewById<EditText>(R.id.etContact2Name)
        val etContact2Phone = findViewById<EditText>(R.id.etContact2Phone)
        val btnSave = findViewById<Button>(R.id.btnSaveContacts)

        // Pre-fill saved values
        etName.setText(manager.getUserName())
        val saved = manager.getContacts()
        if (saved.isNotEmpty()) {
            etContact1Name.setText(saved[0].name)
            etContact1Phone.setText(saved[0].phone)
        }
        if (saved.size > 1) {
            etContact2Name.setText(saved[1].name)
            etContact2Phone.setText(saved[1].phone)
        }

        btnSave.setOnClickListener {
            manager.saveUserName(etName.text.toString().trim())
            val contacts = mutableListOf<EmergencyContact>()
            val n1 = etContact1Name.text.toString().trim()
            val p1 = etContact1Phone.text.toString().trim()
            if (n1.isNotEmpty() && p1.isNotEmpty()) contacts.add(EmergencyContact(n1, p1))
            val n2 = etContact2Name.text.toString().trim()
            val p2 = etContact2Phone.text.toString().trim()
            if (n2.isNotEmpty() && p2.isNotEmpty()) contacts.add(EmergencyContact(n2, p2))
            manager.saveContacts(contacts)
            Toast.makeText(this, "Emergency contacts saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}