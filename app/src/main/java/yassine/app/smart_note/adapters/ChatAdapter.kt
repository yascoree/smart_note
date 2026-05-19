package yassine.app.smart_note.adapters

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import android.widget.TextView
import yassine.app.smart_note.R
import yassine.app.smart_note.models.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage): Int {
        messages.add(message)
        val index = messages.lastIndex
        notifyItemInserted(index)
        return index
    }

    fun removeTypingMessage() {
        val typingIndex = messages.indexOfFirst { it.isTyping }
        if (typingIndex >= 0) {
            messages.removeAt(typingIndex)
            notifyItemRemoved(typingIndex)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_message_user
        } else {
            R.layout.item_message_ai
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        (holder as MessageViewHolder).bind(message)
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: android.view.View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val avatarView: ShapeableImageView? = if (viewType == VIEW_TYPE_AI) {
            itemView.findViewById(R.id.iv_avatar)
        } else {
            null
        }

        private var avatarPulseAnimator: ObjectAnimator? = null

        fun bind(message: ChatMessage) {
            stopAvatarPulse()

            tvMessage.text = if (message.isTyping) "" else message.text
            tvTime.text = dateFormat.format(Date(message.timestamp))

            avatarView?.setImageResource(
                if (message.isTyping) R.drawable.avatar_thinking
                else R.drawable.avatar_normal
            )

            if (message.isTyping) {
                startAvatarPulse()
            }
        }

        fun recycle() {
            stopAvatarPulse()
        }

        private fun startAvatarPulse() {
            val avatar = avatarView ?: return
            avatarPulseAnimator = ObjectAnimator.ofFloat(avatar, "alpha", 1f, 0.4f, 1f).apply {
                duration = 900L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                start()
            }
        }

        private fun stopAvatarPulse() {
            avatarPulseAnimator?.cancel()
            avatarPulseAnimator = null
            avatarView?.alpha = 1f
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? MessageViewHolder)?.recycle()
    }
}