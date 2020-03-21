package com.example.todoapp

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Repository
import org.springframework.http.HttpStatus
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.validation.BindingResult
import org.springframework.validation.annotation.Validated
import org.hibernate.validator.constraints.NotBlank
import javax.validation.constraints.Size

data class Task(val id: Long,
    val content: String,
    val done: Boolean)

class NotFoundException: RuntimeException()

class TaskCreateForm{
  @NotBlank
  @Size(max=20)
  var content: String ?= null
}

class TaskUpdateForm{
  @NotBlank
  @Size(max=20)
  var content: String ?= null
  var done: Boolean = false
}

interface TaskRepository{
  fun create(content: String): Task
  fun update(task: Task)
  fun findAll(): List<Task>
  fun findById(id: Long): Task?
}

class InMemoryTaskRepository: TaskRepository{
  private val tasks: MutableList<Task> = mutableListOf()

  private val maxId: Long
    get() = tasks.map(Task::id).max() ?:0

  override fun create(content: String): Task{
    val id = maxId +1
    val task = Task(id, content, false)
    tasks += task
    return task
  }

  override fun update(task: Task){
    tasks.replaceAll{ t ->
      if(t.id == task.id) task
      else t
    }
  }

  override fun findAll(): List<Task> = tasks.toList()

  override fun findById(id: Long): Task? = tasks.find{it.id == id}
}

@Repository
class JdbcTaskRepository(private val jdbcTemplate: JdbcTemplate): TaskRepository{
  private val rowMapper = RowMapper<Task> { rs, _ ->
    Task(rs.getLong("id"), rs.getString("content"), rs.getBoolean("done"))
  }

  override fun create(content: String): Task{
    jdbcTemplate.update("INSERT INTO task(content) VALUES(?)", content)
    val id = jdbcTemplate.queryForObject("SELECT lastval()", Long::class.java)
//    val id = jdbcTemplate.queryForObject("INSERT INTO task(content) VALUES(?)",Long::class.java, content)
    return Task(id!!, content, false)
  }

  override fun update(task: Task){
    jdbcTemplate.update("UPDATE task SET content = ?, done =? WHERE id = ?",
      task.content,
      task.done,
      task.id)
  }

  override fun findAll(): List<Task> = 
    jdbcTemplate.query("SELECT id, content, done FROM task", rowMapper)

  override fun findById(id: Long): Task ?=
    jdbcTemplate.query("SELECT id, content, done FROM task WHERE id = ?",
      rowMapper,
      id).firstOrNull()
}


@ExceptionHandler(NotFoundException::class)
@ResponseStatus(HttpStatus.NOT_FOUND)
fun handleNotFoundExcetpion(): String = "tasks/not_found"

@Controller
@RequestMapping("tasks")
class TaskController(private val taskRepository: TaskRepository){
  @GetMapping("")
  fun index(model: Model): String{
    val tasks = taskRepository.findAll()
    model.addAttribute("tasks", tasks)
    return "tasks/index"
  }

  @PostMapping("")
  fun create(@Validated form: TaskCreateForm,
    bindingResult: BindingResult): String{
      if(bindingResult.hasErrors())
        return "tasks/new"
      val content = requireNotNull(form.content)
      taskRepository.create(content)
      return "redirect:/tasks"
    }

  @GetMapping("new")
  fun new(form: TaskCreateForm): String{
    return "tasks/new"
  }

  @GetMapping("{id}/edit")
  fun edit(@PathVariable("id") id: Long,
            form: TaskUpdateForm): String{
    val task = taskRepository.findById(id) ?: throw NotFoundException()
    form.content = task.content
    form.done = task.done
    return "tasks/edit"
  }

  @PatchMapping("{id}")
  fun update(@PathVariable("id") id: Long,
      @Validated form: TaskUpdateForm,
      bindingResult: BindingResult): String{
    if(bindingResult.hasErrors())
      return "tasks/edit"
    
      val task = taskRepository.findById(id) ?: throw NotFoundException()
      val newTask = task.copy(content=requireNotNull(form.content),
        done=form.done)
      taskRepository.update(newTask)
      return "redirect:/tasks"
  }
}
