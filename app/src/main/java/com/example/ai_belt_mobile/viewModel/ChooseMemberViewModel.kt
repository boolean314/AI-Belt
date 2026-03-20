package com.example.ai_belt_mobile.viewModel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_belt_mobile.data.local.UserSessionStore
import com.example.ai_belt_mobile.data.model.SetEmergency
import com.example.ai_belt_mobile.network.UserRetrofitClient
import com.example.ai_belt_mobile.ui.adapter.MemberItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChooseMemberViewModel : ViewModel() {

    private val _members = MutableStateFlow<List<MemberItem>>(emptyList())
    val members: StateFlow<List<MemberItem>> = _members.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun loadMembers(context: Context) {
        val session = UserSessionStore.get(context)
        if (session == null || session.id < 0) {
            Toast.makeText(context, "登录信息无效，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _loading.value = true
            try {
                val resp = UserRetrofitClient.instance.getFamilyInfo(session.id)
                if (resp.code == 200) {
                    _members.value = resp.data.map {
                        MemberItem(
                            id = it.id,
                            name = it.name,
                            phone = it.phone,
                            isEmergency = it.isEmergency
                        )
                    }
                } else {
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "获取家庭成员失败，请重试", Toast.LENGTH_SHORT).show()
            } finally {
                _loading.value = false
            }
        }
    }

    fun switchEmergency(context: Context, target: MemberItem) {
        val session = UserSessionStore.get(context)
        if (session == null || session.id < 0) {
            Toast.makeText(context, "登录信息无效，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        // 点当前已是“紧急”不重复请求
        if (target.isEmergency) return

        viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.setEmergencyContact(
                    SetEmergency(
                        id = session.id,
                        emergencyPhone = target.phone
                    )
                )
                Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                if (resp.code == 200) {
                    // 本地强制保持“有且仅有一个紧急”
                    _members.value = _members.value.map { m ->
                        if (m.id == target.id) m.copy(isEmergency = true)
                        else m.copy(isEmergency = false)
                    }

                    UserSessionStore.save(
                        context = context,
                        id = session.id,
                        phone = session.phone,
                        name = session.name,
                        mail = session.mail,
                        identity = session.identity,
                        code = session.code,
                        emergency = target.phone
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "设置紧急联系人失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }
}