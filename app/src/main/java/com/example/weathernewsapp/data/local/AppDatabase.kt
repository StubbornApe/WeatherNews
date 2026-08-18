package com.example.weathernewsapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.weathernewsapp.data.local.dao.WeatherDao
import com.example.weathernewsapp.data.local.entity.WeatherEntity

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * AppDatabase —— Room 数据库持有者(整个 App 只需要一个实例:单例)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【这个类在做什么?一句话总结】
 *   它是"连接你代码和 SQLite 数据库文件"的桥梁:
 *     · 告诉 Room"库里有哪些表"(entities)
 *     · 告诉 Room"数据库版本号是几"(version)
 *     · 暴露"拿到 DAO 实例"的方法(weatherDao())
 *     · 通过单例保证全 App 共享一个数据库连接(避免锁冲突/内存泄漏)
 *
 * 【为什么是 abstract class 而不是普通 class?】
 *   Room 在编译期通过 KSP 自动生成一个子类 `AppDatabase_Impl`(继承自本类),
 *   这个子类实现了抽象方法 weatherDao(),也实现了数据库打开/建表/迁移的所有逻辑。
 *
 *   你写: abstract class AppDatabase : RoomDatabase()
 *   KSP 生成: class AppDatabase_Impl : AppDatabase() {
 *                  override fun weatherDao(): WeatherDao = WeatherDao_Impl(this)
 *                  // ...还有好多 Room 内部逻辑...
 *              }
 *
 *   所以你不要 new AppDatabase()——交给 Room.databaseBuilder(...).build()。
 *
 * 【继承 RoomDatabase 是什么?】
 *   RoomDatabase 是 Room 框架提供的基类,内部持有:
 *     · 一个 SupportSQLiteOpenHelper(类似原生 SQLiteOpenHelper)
 *     · 一个 SQL 预编译语句缓存
 *     · 各个 DAO 实例的缓存
 *     · 协程/事务调度器
 *   你的子类只需要声明"我有什么表、有哪些 DAO",其它都由基类 + KSP 生成代码处理。
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Database(
    entities = [WeatherEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    // ⭐ 抽象函数:拿到天气表的 DAO
    //
    // 【为什么没有方法体?】
    //   因为 KSP 生成的 AppDatabase_Impl 会实现它:
    //     override fun weatherDao(): WeatherDao = WeatherDao_Impl(this)
    //   你只需要声明"我有这个 DAO",Room 负责 new 实例并缓存。
    //
    // 【怎么用?】
    //   val db = AppDatabase.getInstance(context)
    //   val dao = db.weatherDao()     // ← 这一行就拿到了 DAO
    //   dao.insert(weather)           // ← 直接用
    abstract fun weatherDao(): WeatherDao

    // ═══════════════════════════════════════════════════════════════════════
    //  单例实现(companion object + 双重检查锁 DCL)
    // ═══════════════════════════════════════════════════════════════════════
    //
    // 【为什么要单例?】
    //   · Room.databaseBuilder(...).build() 是昂贵操作:会打开 SQLite 文件、
    //     预编译 SQL、做版本检查/迁移,每次都 new 会卡主线程
    //   · 多个 AppDatabase 实例同时写同一个 .db 文件会引发"database is locked"异常
    //   · 所以全 App 只建一次,之后都复用
    //
    // 【Java/Kotlin 的 static 等价写法】
    //   Kotlin 没有 static 关键字,要写"类级别的静态成员"就用 companion object { }。
    //   里面的属性/方法可以通过 AppDatabase.getInstance(...) 直接调用,无需实例。
    companion object {
        // ⭐ 单例实例变量
        //
        // 【@Volatile 是干嘛的?】
        //   JVM/ART 有"线程工作内存缓存"——一个线程改了 INSTANCE,
        //   另一个线程可能在自己的 CPU 缓存里看到旧值(还是 null)。
        //   @Volatile 告诉虚拟机:"这个字段的读写都走主内存,别缓存",
        //   保证一个线程赋值后其他线程立刻能看到。
        //
        // 【为什么是 AppDatabase? 而不是 AppDatabase ?】
        //   ? 表示可空:在第一次 getInstance 之前,INSTANCE 是 null。
        //   赋值后变成非 null。
        //
        // 【private set】
        //   这里没写 set,但因为是 private var,只有本类能修改。
        //   外部只能通过 getInstance() 访问,不能直接 INSTANCE = xxx。
        @Volatile
        private var dbInstance: AppDatabase? = null

        /**
         * 获取数据库单例。线程安全。
         *
         * 【双重检查锁(Double-Checked Locking, DCL)】
         *   目的:既保证多线程下只创建一个实例,又避免每次都进同步块(性能好)。
         *
         *   流程:
         *   ┌─────────────────────────────────────────────────────────────────┐
         *   │ 1) 第一次检查(无锁):INSTANCE != null 就直接返回               │
         *   │    好处:99% 的场景实例已存在,不用进 synchronized,几乎零开销   │
         *   ├─────────────────────────────────────────────────────────────────┤
         *   │ 2) synchronized(this):JVM 级别的互斥锁                        │
         *   │    同一时间只有一个线程能进入这个块,其它线程阻塞等待           │
         *   ├─────────────────────────────────────────────────────────────────┤
         *   │ 3) 第二次检查(有锁):再判一次 INSTANCE 是否为 null              │
         *   │    必要性:第一个线程在等锁期间,可能有别的线程已经创建了实例     │
         *   │    如果不重新检查,会重复创建,违反单例                         │
         *   ├─────────────────────────────────────────────────────────────────┤
         *   │ 4) 真的为 null 才 Room.databaseBuilder(...).build() 创建实例    │
         *   │    把新实例赋值给 INSTANCE,返回                                │
         *   └─────────────────────────────────────────────────────────────────┘
         *
         * @param context 任意 Context(Activity/Fragment/App 都行,内部会转 applicationContext)
         * @return 全局唯一的 AppDatabase 实例
         */
        fun getInstance(context: Context): AppDatabase {
            // ── 第一次检查:已经有实例就直接返回,无锁,快 ──────────────
            // INSTANCE?.let { return it } 的含义:
            //   · 如果 INSTANCE != null,执行 let 块(把非空值作为 it),
            //     return it 直接返回这个已有实例
            //   · 如果 INSTANCE == null,什么都不做,继续往下走
            //
            // 这是"快速路径":99% 的调用都在这里命中,不会进入同步块。
            dbInstance?.let { return it }

            // ── 进入同步块 ─────────────────────────────────────────────
            // synchronized(this):以 AppDatabase.companion 的类对象为锁。
            //
            // 线程 A 和线程 B 同时调用 getInstance(),都在第一次检查看到 null:
            //   · 线程 A 先进 synchronized,线程 B 在外面等着
            //   · 线程 A 创建实例 → 赋值 INSTANCE → 退出 synchronized → 返回
            //   · 线程 B 进入 synchronized,此时第二次检查发现 INSTANCE != null
            //     → 不创建新实例,直接复用线程 A 创建的实例 ✓
            return synchronized(this) {
                // ── 第二次检查:等锁期间可能别的线程已经建好了 ──────────
                val existing = dbInstance
                if (existing != null) {
                    // 已有实例(等锁时别的线程建的),直接返回
                    existing
                } else {
                    // ── 真的是第一次,创建实例 ──────────────────────
                    //
                    // Room.databaseBuilder(...) 是 Room 提供的 Builder 模式 API:
                    //
                    // 参数 1:context.applicationContext
                    //   ⭐ 必须用 applicationContext!为什么?
                    //   Activity/Fragment 的 context 跟生命周期绑定(旋转、finish 后销毁)。
                    //   AppDatabase 持有 context 引用用来访问数据库文件路径,
                    //   如果传 Activity context,Activity 销毁后无法被 GC,内存泄漏。
                    //   applicationContext 是 App 级单例,跟进程同寿命,安全。
                    //
                    // 参数 2:AppDatabase::class.java
                    //   告诉 Room"我要建的是哪个 @Database 类"。
                    //   Room 会通过反射/生成代码找到 AppDatabase_Impl 类。
                    //
                    // 参数 3:"weather.db"
                    //   数据库文件名,存到 /data/data/com.example.weathernewsapp/databases/weather.db
                    //   可以随便起名,Database Inspector 里看到的就是这个文件名。
                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "weather.db",
                        )
                            // ⭐ fallbackToDestructiveMigration() —— 开发期策略,上线前必须换!
                            //
                            // 什么时候触发?
                            //   你改了 Entity(加了个字段)/加了张表,把 version 从 1 改成 2,
                            //   但没有提供 Migration(1,2) 告诉 Room"旧库怎么改成新结构",
                            //   默认情况下 Room 会崩:
                            //     "IllegalStateException: Migration didn't handle..."
                            //
                            // 加了这一行后,Room 遇到版本不匹配时会:
                            //   1. DROP 所有旧表(用户数据全部丢失!)
                            //   2. 按新 Entity 重新 CREATE TABLE
                            //   3. App 正常启动(但是数据是空的)
                            //
                            // 开发阶段很方便:改了 Entity 不用写迁移,卸了重装也行。
                            // ⚠️ 生产上线必须去掉这一行,改用 .addMigrations(MIGRATION_1_2, ...),
                            //    否则用户升级 App 后缓存数据全丢,体验非常糟糕。
                            .fallbackToDestructiveMigration()
                            // build():执行建库操作(如果是首次创建,就在这里 CREATE TABLE),
                            // 返回一个 AppDatabase_Impl 实例(即 AppDatabase 的子类)。
                            .build()

                    // 把新实例存到 @Volatile 的 dbInstance 字段,下次调用走快速路径。
                    dbInstance = instance

                    // 同步块最后一行表达式自动作为返回值,返回给外层的 return synchronized(...)
                    instance
                }
            }
        }
    }
}
