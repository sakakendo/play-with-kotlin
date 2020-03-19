import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import spark.ResponseTransformer
import spark.Spark.path
import spark.Spark.get
import spark.Spark.post
import spark.Spark.patch
import spark.Spark.delete
import spark.Spark.halt
import spark.Route
import spark.Request

data class Task(
    val id: Long,
    val content: String,
    val done: Boolean
)


class JsonTransformer(private val objectMapper: ObjectMapper): ResponseTransformer{
    override fun render(model: Any?): String = objectMapper.writeValueAsString(model)
}

class TaskRepository{
    private val tasks: MutableList<Task> = mutableListOf()
    private val maxId: Long
        get() = tasks.map(Task::id).max()?: 0
    fun findAll(): List<Task> = tasks.toList()

    fun create(content: String): Task{
        val id = maxId+1
        val task = Task(id, content, false)
        tasks += task
        return task
    }

    fun findById(id: Long): Task? = tasks.find{ it.id == id}

    fun update(task: Task){
        tasks.replaceAll{t->
            if(t.id == task.id) task
            else t
        }
    }

    fun delete(task: Task){
        tasks.removeIf{ (id) -> id == task.id}
    }
}

data class TaskCreateRequest(
    @JsonProperty("content", required=true) val content: String
)

data class TaskUpdateRequest(
    @JsonProperty("content") val content: String?,
    @JsonProperty("done") val done: Boolean
)

inline fun <reified T:Any>ObjectMapper.readValue(src: ByteArray): T? =
    try{
        readValue(src, T::class.java)
    }catch(e: Exception){
        null
    }

class TaskController (private val objectMapper: ObjectMapper,
                      private val taskRepository: TaskRepository){
    private val Request.task: Task?
        get(){
            val id = params("id").toLongOrNull()
            return id?.let(taskRepository::findById)
        }

    fun index(): Route = Route{ req, res ->
        taskRepository.findAll()
    }
    fun create(): Route = Route{req, res->
        val request: TaskCreateRequest = objectMapper.readValue(req.bodyAsBytes()) ?: throw halt()
        val task = taskRepository.create(request.content)
        res.status(201)
        task
    }

    fun show(): Route= Route{ req, res ->
        val id = req.params("id").toLongOrNull()
        println(id)
        id?.let(taskRepository::findById) ?:throw halt(404)
    }

    fun update(): Route = Route{req, res ->
        val request: TaskUpdateRequest =
            objectMapper.readValue(req.bodyAsBytes()) ?: throw halt(400)
        val task = req.task ?: throw halt(404)
        val newTask = task.copy(
            content = request.content ?: task.content,
            done = request.done ?: task.done
        )
        taskRepository.update(newTask)
        res.status(204)
    }

    fun destroy(): Route = Route{ req, res->
        val task = req.task ?: throw halt(404)
        taskRepository.delete(task)
        res.status(204)
    }
}

fun main(args: Array<String>){
    val objectMapper = ObjectMapper().registerKotlinModule()
    val jsonTransformer = JsonTransformer(objectMapper)
    val taskRepository = TaskRepository()
    val taskController = TaskController(objectMapper, taskRepository)
    path("tasks"){
        get("", taskController.index(), jsonTransformer)
        post("", taskController.create(), jsonTransformer)
        get("/:id", taskController.show(), jsonTransformer)
        patch("/:id", taskController.update(), jsonTransformer)
        delete("/:id", taskController.destroy(), jsonTransformer)
    }

}