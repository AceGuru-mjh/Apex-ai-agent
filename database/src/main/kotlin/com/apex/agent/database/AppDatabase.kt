package com.apex.agent.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.apex.agent.database.dao.*
import com.apex.agent.database.entity.*

@Database(
    entities = [
        User::class, Task::class,
        Permission::class, Role::class,
        UserRole::class, RolePermission::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun taskDao(): TaskDao
    abstract fun userWithTasksDao(): UserWithTasksDao
    abstract fun permissionDao(): PermissionDao
    abstract fun roleDao(): RoleDao
    abstract fun userRoleDao(): UserRoleDao
    abstract fun rolePermissionDao(): RolePermissionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apex_agent_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(SEED_CALLBACK)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'
                """)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.beginTransaction()
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS permissions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            category TEXT NOT NULL DEFAULT 'system',
                            createdAt INTEGER NOT NULL
                        )
                    """)
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_permissions_name ON permissions(name)")

                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS roles (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            level INTEGER NOT NULL DEFAULT 0,
                            isSystem INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL
                        )
                    """)
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_roles_name ON roles(name)")

                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS user_roles (
                            userId INTEGER NOT NULL,
                            roleId INTEGER NOT NULL,
                            grantedBy TEXT,
                            grantedAt INTEGER NOT NULL,
                            expiresAt INTEGER,
                            PRIMARY KEY (userId, roleId),
                            FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE,
                            FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE
                        )
                    """)
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_user_roles_userId ON user_roles(userId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_user_roles_roleId ON user_roles(roleId)")

                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS role_permissions (
                            roleId INTEGER NOT NULL,
                            permissionId INTEGER NOT NULL,
                            PRIMARY KEY (roleId, permissionId),
                            FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE,
                            FOREIGN KEY (permissionId) REFERENCES permissions(id) ON DELETE CASCADE
                        )
                    """)
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_role_permissions_roleId ON role_permissions(roleId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_role_permissions_permissionId ON role_permissions(permissionId)")

                    insertDefaultPermissions(database)
                    insertDefaultRoles(database)
                    seedDefaultAdminUser(database)
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            }
        }

        private val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.beginTransaction()
                try {
                    insertDefaultPermissions(db)
                    insertDefaultRoles(db)
                    seedDefaultAdminUser(db)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private fun insertDefaultPermissions(database: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val perms = listOf(
                "('agent:tools', '允许使用工具', 'agent', $now)",
                "('agent:internet', '允许访问网络', 'agent', $now)",
                "('agent:read', '允许读取文件', 'agent', $now)",
                "('agent:write', '允许写入文件', 'agent', $now)",
                "('agent:call_agents', '允许调用其他Agent', 'agent', $now)",
                "('api:tasks:create', '创建任务', 'api', $now)",
                "('api:tasks:read', '读取任务', 'api', $now)",
                "('api:tasks:update', '更新任务', 'api', $now)",
                "('api:tasks:delete', '删除任务', 'api', $now)",
                "('api:tasks:cancel', '取消任务', 'api', $now)",
                "('api:tasks:pause', '暂停任务', 'api', $now)",
                "('api:tasks:resume', '恢复任务', 'api', $now)",
                "('api:stats:read', '读取统计信息', 'api', $now)",
                "('api:files:upload', '上传文件', 'api', $now)",
                "('users:manage', '管理用户', 'system', $now)",
                "('roles:manage', '管理角色', 'system', $now)",
                "('permissions:manage', '管理权限', 'system', $now)",
                "('system:admin', '系统管理员权限', 'system', $now)",
                "('system:settings', '修改系统设置', 'system', $now)",
                "('system:audit', '查看审计日志', 'system', $now)",
                "('file:read', '读取文件系统中的文件', 'file', $now)",
                "('file:write', '写入文件系统中的文件', 'file', $now)",
                "('file:delete', '删除文件系统中的文件', 'file', $now)",
                "('engine:shell:execute', '允许通过 EngineService 执行 Shell 命令', 'engine', $now)"
            )
            database.execSQL(
                "INSERT OR IGNORE INTO permissions (name, description, category, createdAt) VALUES ${perms.joinToString(",")}"
            )
        }

        private fun insertDefaultRoles(database: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            database.execSQL(
                "INSERT OR IGNORE INTO roles (name, description, level, isSystem, createdAt) VALUES " +
                    "('super_admin', '超级管理员 - 全部权限', 5, 1, $now)," +
                    "('admin', '管理员 - 高级权限', 4, 1, $now)," +
                    "('user', '普通用户 - 标准权限', 1, 1, $now)," +
                    "('guest', '访客 - 只读权限', 0, 1, $now)"
            )
        }

        /**
         * 种子默认管理员用户 + RBAC 关联：
         *   1. 创建 id=1 的 admin 用户（INSERT OR IGNORE 保证幂等）
         *   2. 把 super_admin 角色授予 userId=1
         *   3. 把 super_admin 关联到所有权限（role_permissions 全量赋权）
         *
         * 这一步是 RBAC 真正生效的关键——仅有 permissions / roles 表而没有
         * user_roles / role_permissions 行的话，hasPermission() 永远返回 false。
         *
         * EngineService.executeCommand 默认以 userId=1 + "engine:shell:execute"
         * 调用 hasPermission；本 seed 保证 super_admin 拥有该权限。
         */
        private fun seedDefaultAdminUser(database: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            // 1. 默认 admin 用户（显式 id=1，方便 EngineService 等下游写死 DEFAULT_ADMIN_USER_ID=1L）
            database.execSQL(
                "INSERT OR IGNORE INTO users (id, name, email, createdAt, updatedAt) " +
                    "VALUES (1, 'admin', 'admin@apex.local', $now, $now)"
            )
            // 2. 授予 super_admin 角色（通过子查询解析 roleId，避免硬编码 id）
            database.execSQL(
                "INSERT OR IGNORE INTO user_roles (userId, roleId, grantedBy, grantedAt) " +
                    "VALUES (1, (SELECT id FROM roles WHERE name='super_admin' LIMIT 1), 'system', $now)"
            )
            // 3. super_admin 全量赋权（CROSS JOIN 当前所有权限）
            database.execSQL(
                "INSERT OR IGNORE INTO role_permissions (roleId, permissionId) " +
                    "SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name='super_admin'"
            )
        }
    }
}
