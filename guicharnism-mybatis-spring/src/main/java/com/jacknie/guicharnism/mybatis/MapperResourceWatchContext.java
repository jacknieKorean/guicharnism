/**
 * Created by jacknie, 2017. 2. 21.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.jacknie.guicharnism.mybatis;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.PathMatcher;

public class MapperResourceWatchContext {

	private final Logger logger = LoggerFactory.getLogger(getClass());	
	private final MultiValueMap<String, Resource> resourceMap = new LinkedMultiValueMap<String, Resource>();
	private final Map<String, MapperResourceWatcher> watcherMap = new HashMap<String, MapperResourceWatcher>();
	private final PathMatcher pathMatcher = new AntPathMatcher();
	
	private String reloadTargetFilePattern;
	private Configuration configuration;
	private MapperResourceWatcherFactory watcherFactory;
	
	public MapperResourceWatcherFactory resolveFactory() {
		Assert.hasLength(reloadTargetFilePattern);
		//TODO: 1.6 환경 개발자 고려 VFS Watcher 개발 예정
		if (this.watcherFactory == null) {
			ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
			String className = "com.jacknie.guicharnism.mybatis.support.NioMapperResourceWatcherFactory";
			try {
				if (ClassUtils.isPresent(className, classLoader)) {
					logger.debug("[{}] factory instance loading...", className);
					Class<?> clazz = classLoader.loadClass(className);
					Constructor<?> constructor = clazz.getConstructor(getClass());
					Object instance = constructor.newInstance(this);
					this.watcherFactory = (MapperResourceWatcherFactory) instance;
				}
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
		return this.watcherFactory;
	}
	
	public boolean isAlreadyWatched(Resource resource) throws IOException {
		String directory = resource.getFile().getAbsolutePath();
		if (watcherMap.containsKey(directory)) {
			MapperResourceWatcher watcher = watcherMap.get(directory);
			if (watcher != null) {
				return watcher.isWatched();
			}
		}
		return false;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	public void setReloadTargetFilePattern(String reloadTargetFilePattern) {
		if (pathMatcher.isPattern(reloadTargetFilePattern)) {
			this.reloadTargetFilePattern = reloadTargetFilePattern;
		}
		else {
			throw new IllegalArgumentException("\"" + reloadTargetFilePattern + "\" is not ant pattern.");
		}
	}
	
	public boolean isMatchedFileName(String fileName) {
		return pathMatcher.match(reloadTargetFilePattern, fileName);
	}
	
	public void addWatcher(MapperResourceWatcher watcher) throws IOException {
		
		Resource resource = watcher.getTargetResource();
		
		if (!resource.exists()) {
			throw new FileNotFoundException(resource.getDescription());
		}
		
		File watchTargetDirectory = resource.getFile();
		if (!watchTargetDirectory.isDirectory()) {
			throw new IllegalArgumentException("File object is not referenced directory.");
		}
		
		String directory = watchTargetDirectory.getAbsolutePath();
		watcherMap.put(directory, watcher);
		
		File[] children = watchTargetDirectory.listFiles(new FileFilter() {

			@Override
			public boolean accept(File file) {
				return file.isFile() && isMatchedFileName(file.getName());
			}
			
		});
		if (children != null) {
			for (File child : children) {
				Resource childResource = new FileSystemResource(child);
				resourceMap.add(directory, childResource);
			}
		}
	}
	
	public void removeWatchedResource(MapperResourceWatcher watcher) throws IOException {
		
		Resource resource = watcher.getTargetResource();
		String directory = resource.getFile().getAbsolutePath();
		resourceMap.remove(directory);
	}
	
	public MapperResourceWatcher getWatcher(File directory) {
		return watcherMap.get(directory.getAbsolutePath());
	}
	
	public MapperResourceWatcher getWatcher(Resource resource) throws IOException {
		return getWatcher(resource.getFile());
	}
	
	public Collection<MapperResourceWatcher> getWatchers() {
		return watcherMap.values();
	}
	
	public Set<String> getWatchedDirectories() {
		return watcherMap.keySet();
	}
	
	public List<Resource> getResources(String directory) {
		return resourceMap.get(directory);
	}
}