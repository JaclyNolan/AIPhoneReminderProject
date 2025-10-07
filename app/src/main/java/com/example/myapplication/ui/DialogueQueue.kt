package com.example.myapplication.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Simple thread-safe queue backed by a MutableStateFlow so Compose UI can observe it.
object DialogueQueue {
    private val lock = Any()
    private val _state = MutableStateFlow<List<DialogueEntry>>(emptyList())
    val state: StateFlow<List<DialogueEntry>> = _state.asStateFlow()

    fun enqueue(entry: DialogueEntry) = enqueue(listOf(entry))

    fun enqueue(entries: List<DialogueEntry>) {
        synchronized(lock) {
            val curr = _state.value.toMutableList()
            curr.addAll(entries)
            _state.value = curr
        }
    }

    /** Put entries at the front of the queue (preserving their order). */
    fun enqueueFront(entries: List<DialogueEntry>) {
        synchronized(lock) {
            val curr = _state.value.toMutableList()
            val newList = mutableListOf<DialogueEntry>()
            newList.addAll(entries)
            newList.addAll(curr)
            _state.value = newList
        }
    }

    /** Replace the existing head element with the provided entries. If the queue is empty, behaves like enqueueFront. */
    fun replaceHeadWith(entries: List<DialogueEntry>) {
        synchronized(lock) {
            val curr = _state.value.toMutableList()
            if (curr.isEmpty()) {
                val newList = mutableListOf<DialogueEntry>()
                newList.addAll(entries)
                _state.value = newList
                return
            }
            // remove current head and insert new entries at front
            curr.removeAt(0)
            val newList = mutableListOf<DialogueEntry>()
            newList.addAll(entries)
            newList.addAll(curr)
            _state.value = newList
        }
    }

    /** Removes and returns the head entry, or null if empty. */
    fun dequeue(): DialogueEntry? {
        synchronized(lock) {
            val curr = _state.value.toMutableList()
            if (curr.isEmpty()) return null
            val head = curr.removeAt(0)
            _state.value = curr
            return head
        }
    }

    fun clear() {
        synchronized(lock) { _state.value = emptyList() }
    }

    fun snapshot(): List<DialogueEntry> = _state.value.toList()
}
