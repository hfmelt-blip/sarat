package com.sarat.app.ui.kitchen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.Repository
import com.sarat.app.data.SavedRecipe
import com.sarat.app.data.db.ShoppingEntity
import com.sarat.app.data.gemini.MealSuggestion
import com.sarat.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipesViewModel(private val repo: Repository) : ViewModel() {
    val saved: StateFlow<List<SavedRecipe>> =
        repo.recipes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** تفاصيل وصفة محفوظة: الكمية، المشاركة، المشتريات، والتسجيل. */
class RecipeDetailViewModel(private val repo: Repository, private val id: Long) : ViewModel() {
    var recipe by mutableStateOf<SavedRecipe?>(null)
        private set
    var portion by mutableStateOf(1.0)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { recipe = repo.getRecipe(id) }
    }

    /** الوصفة بعد ضرب الكمية بعدد الحصص. */
    val scaled: MealSuggestion?
        get() = recipe?.recipe?.let { r -> r.copy(ingredients = r.ingredients.map { it.scaled(portion) }) }

    fun choosePortion(p: Double) { portion = p }

    fun addMissingToShopping() {
        val s = scaled ?: return
        viewModelScope.launch {
            val added = s.ingredients.filter { !it.available }.count { repo.addShopping(it.name, it.amount) }
            message = if (added > 0) "انضاف $added للمشتريات" else "الناقص موجود أصلاً بالقائمة"
        }
    }

    fun addAllToShopping() {
        val s = scaled ?: return
        viewModelScope.launch {
            val added = s.ingredients.count { repo.addShopping(it.name, it.amount) }
            message = if (added > 0) "انضاف $added للمشتريات" else "كل المكونات موجودة بالقائمة"
        }
    }

    fun eat(onDone: () -> Unit) {
        val s = scaled ?: return
        viewModelScope.launch {
            repo.addMeal(s.toAnalysis(), Dates.todayEpoch(), Dates.nowMinute(), "PLAN")
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteRecipe(id)
            onDone()
        }
    }

    fun clearMessage() { message = null }
}

class ShoppingViewModel(private val repo: Repository) : ViewModel() {
    val items: StateFlow<List<ShoppingEntity>> =
        repo.shopping.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var input by mutableStateOf("")

    fun add() {
        val text = input.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            // يقبل أكثر من شي مفصول بفاصلة
            text.split('،', ',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { repo.addShopping(it) }
            input = ""
        }
    }

    fun toggle(item: ShoppingEntity) {
        viewModelScope.launch { repo.setShoppingDone(item.id, !item.done) }
    }

    fun delete(item: ShoppingEntity) {
        viewModelScope.launch { repo.deleteShopping(item.id) }
    }

    fun clearBought() {
        viewModelScope.launch { repo.clearBoughtShopping() }
    }
}
